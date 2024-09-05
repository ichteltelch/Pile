package pile.interop.preferences;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.ref.WeakReference;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.WeakHashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

import pile.aspect.AlwaysValid;
import pile.aspect.Dependency;
import pile.aspect.combinations.ReadListenDependency;
import pile.aspect.combinations.ReadWriteDependency;
import pile.aspect.combinations.ReadWriteListenValue;
import pile.aspect.listen.ListenValue;
import pile.aspect.listen.ValueListener;
import pile.aspect.suppress.Suppressor;
import pile.impl.EarlyMutRef;
import pile.impl.Independent;
import pile.impl.Piles;
import pile.interop.wait.WaitService;
import pile.specialized_bool.combinations.ReadListenDependencyBool;
import pile.utils.Bijection;
import pile.utils.Functional;
import pile.utils.WeakCleanupWithRunnable;

/**
 * A reactive value that is reflected in real time in a {@link Preferences} node.
 * 
 * This class does not implement {@link Dependency}; 
 * @param <T>
 */
public class SynchronizingFilesBackedValue<T> implements 
ReadWriteListenValue<T>, 
ListenValue.Managed, 
AlwaysValid<T>
{
	private static final Logger logger = Logger.getLogger("FileBackedValue");

	static interface FileCodec<T> {
		public void encode(T value, Path path) throws IOException;
		public T decode(Path path) throws IOException;
	}
	public static FileCodec<String> STRING_CODEC = new FileCodec<String>() {

		@Override
		public void encode(String value, Path path) throws FileNotFoundException, IOException {
			if(value==null)
				return;
			try(
					FileOutputStream fos = new FileOutputStream(path.toFile());
					OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
					BufferedWriter bw = new BufferedWriter(osw);
					) 
			{
				bw.write(value);
			}
		}

		@Override
		public String decode(Path path) throws FileNotFoundException, IOException {
			try(
					FileInputStream fis = new FileInputStream(path.toFile());
					InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
					BufferedReader br = new BufferedReader(isr);
					){
				StringBuilder result = new StringBuilder();
				String line;
				boolean first = true;
				while((line = br.readLine())!= null) {
					if(first) first = false; else result.append('\n');
					result.append(line);
				}
				return result.toString();
			}

		}
	};
	public static <T> FileCodec<T> viaString(Bijection<T, String> encode){
		return new FileCodec<T>() {
			@Override
			public void encode(T value, Path path) throws IOException {
				STRING_CODEC.encode(encode.apply(value), path);
			}

			@Override
			public T decode(Path path) throws IOException {
				return encode.applyInverse(STRING_CODEC.decode(path));
			}
		};
	}

	ReadListenDependency<? extends List<Path>> file;
	FileCodec<T> codec;
	/**
	 * Will be set to <code>null</code> once the value is initialized
	 */
	private Supplier<? extends T> defaultValue;
	private T currentValue;
	private BiPredicate<? super T, ? super T> equivalence = ReadWriteDependency.DEFAULT_BIJECT_EQUIVALENCE;
	long timestamp;
	long size;

	/**
	 * Call this if you need to depend on the value, as it does not implement {@link Dependency}.
	 * 
	 * Convenience method; redirects to {@link #writableValidBuffer_memo()}.
	 * @return
	 */
	public Independent<T> asDependency(){
		return writableValidBuffer_memo();
	}
	ValueListener fileListener = e->{
		timestamp = -100000;
		size=-2;
		read();
	};
	ValueListener fileListenerWeakHandle;
	volatile ListenValue.ListenerManager manager;
	public SynchronizingFilesBackedValue(
			ReadListenDependency<? extends Collection<? extends Path>> file, 
					FileCodec<T> codec, 
					Supplier<? extends T> defaultValue) {
		this.codec = codec;
		this.file = file.<List<Path>>map(SynchronizingFilesBackedValue::canonicalize);
		this.defaultValue = defaultValue;
		fileListenerWeakHandle = file.addWeakValueListener(fileListener);
		read();

	}
	public static <T> SynchronizingFilesBackedValue<T> forSingleFile(
			ReadListenDependency<? extends Path> file, 
			FileCodec<T> codec, 
			Supplier<? extends T> defaultValue) {
		return new SynchronizingFilesBackedValue<T>(file.map(Collections::singleton), codec, defaultValue);
	}
	static List<Path> canonicalize(Collection<? extends Path> paths) {
		if(paths==null) {
			return Collections.emptyList();
		}
		return paths
				.stream()
				.filter(Functional.IS_NOT_NULL)
				.map(p->{
					try {
						return canonicalPathInstance(p.toFile().getCanonicalFile().toPath());
					} catch (IOException e) {
						return null;
					}
				})
				.filter(Functional.IS_NOT_NULL)
				.distinct()
				.sorted(Path::compareTo)
				.collect(Collectors.toList());

	}
	@Override
	public T get() {
		if(isDestroyed())
			throw new IllegalStateException("Value is destroyed");
		return currentValue;
	}
	public T set(T value) {
		write(value, false);
		return value;
	}
	@Override
	public void accept(T t) {
		write(t, false);
	}
	public void reset() {
		List<Path> paths = file.get();
		for(Path path : paths) {
			try {
				Files.delete(path);
			} catch (IOException e) {
				logger.log(Level.WARNING, "Error deleting backing file " + path, e);
			}
		}
	}

	private void write(T value, boolean force) {
		value = applyCorrection(value);
		if(_write(value, force, null, FileTime.fromMillis(System.currentTimeMillis()), false, false))
			fireValueChange();
	}
	private void read() {
		if(_read())
			fireValueChange();
	}
	private synchronized boolean _write(T value, boolean force, Path spare, FileTime _time, boolean onlyIfNotExists, boolean orOld) {
		if(isDestroyed())
			throw new IllegalStateException("Value is destroyed");
		synchronized (this) {
			if(_time==null)
				_time = FileTime.fromMillis(timestamp);

			final FileTime time = _time;

			if(!force && defaultValue==null && equivalence.test(value, currentValue))
				return false;
			
			List<Path> fs = file.get();
			allExist:if(false && onlyIfNotExists) {
				for(Path p: fs) {
					if(!Files.exists(p))
						break allExist;
					if(orOld) {
						try {
							if(Files.getLastModifiedTime(p).toMillis()>=time.toMillis()) {
								break allExist;
							}
						} catch (IOException e) {
							logger.log(Level.WARNING, "Error reading metadata of backing file "+p, e);
							break allExist;
						}
					}

				}
				//all files exist (and are current, optionally), nothing to do
				return false;
			}


			timestamp=time.toMillis();
			currentValue=value;
			defaultValue = null;


			int code;
			try {
				code = lockingAll(fs.iterator(), spare,  false, ()->{
					Path f = newest(fs, null);

					if(f!=null) {
						try {
							if(Files.getLastModifiedTime(f).toMillis()>timestamp) {
								return 2;
							}
						}catch (IOException e) {
							logger.log(Level.WARNING, "Error reading metadata of backing file "+f, e);
						}
					}

					//					long maxTime = Long.MIN_VALUE;
					for(Path p: fs) {
						if(p.equals(spare))
							continue;
						if(onlyIfNotExists) {
							if(orOld) {
								if(Files.exists(p) && Files.getLastModifiedTime(p).toMillis()>=time.toMillis()) {
									continue;
								}
							}else if(Files.exists(p)) {
								continue;
							}
						}
						try {
							codec.encode(currentValue, p);
							logger.log(Level.INFO, dependencyName()+" wrote value to backing file "+p+": "+currentValue+ " @ time "+time);
							//							maxTime = Math.max(maxTime, Files.getLastModifiedTime(p).toMillis());
							size = Files.size(p);
							Files.setLastModifiedTime(p, time);
						} catch (IOException e) {
							logger.log(Level.WARNING, "Error writing to backing file "+f, e);
						}
					}
					return 1;
				});
			} catch (Exception e) {
				logger.log(Level.SEVERE, "Error while writing to backing files", e);
				return false;
			}
			if(code!=2) {
				notifyAll();
				return code==1;
			}
		}
		read();


		return true;
	}
	private Path newest(List<Path> fs, Predicate<? super Path> filter) {
		Path newest = null;
		long newestTime = Long.MIN_VALUE;
		for(Path p : fs) {
			if(!Files.exists(p))
				continue;
			if(filter!=null &&!filter.test(p))
				continue;

			long time;
			try {
				time = Files.getLastModifiedTime(p).toMillis();
			} catch (IOException e) {
				logger.log(Level.WARNING, "Error reading metadata of backing file "+p, e);
				continue;
			}
			if(newest==null) {
				newest = p;
				newestTime = time;
			}else {

				if(time>newestTime) {
					newest = p;
					newestTime = time;
				}
			}
		}
		return newest;
	}

	private static <T> T lockingAll(Iterator<Path> iterator, Path spare, boolean shared, Callable<T> object) throws Exception {
		if(iterator.hasNext()) {
			Path next = iterator.next();
			if(next.equals(spare) || !Files.exists(next)) {
				return lockingAll(iterator, spare, shared, object);
			}
			boolean succesfullyLocked=false;
			Lock ll = canonicalPathLock(next);
			ll.lock();
			try(
					FileChannel c = FileChannel.open(next, shared? StandardOpenOption.READ: StandardOpenOption.WRITE);
					FileLock l = c.lock(0, Long.MAX_VALUE, shared)
					){
				succesfullyLocked = true;
				return lockingAll(iterator, spare, shared, object);
			}catch (IOException e) {
				if(!succesfullyLocked) {
					logger.log(Level.WARNING, "Error locking backing file "+next, e);
					return lockingAll(iterator, spare, shared, object);
				}else {
					throw e;
				}
			}finally {
				ll.unlock();
			}

		}else {
			return object.call();
		}
	}

	public Future<?> autoPoll(ScheduledExecutorService scheduler, long period) {
		return autoPoll(this, scheduler, period);
	}

	public Future<?> autoPoll(SynchronizingFilesBackedValue<?> self, ScheduledExecutorService scheduler, long periodMillis) {
		EarlyMutRef<Future<?>> job = new EarlyMutRef<>();
		WeakCleanupWithRunnable<SynchronizingFilesBackedValue<?>> selfRef = new WeakCleanupWithRunnable<>(self, ()->job.get().cancel(false));
		job.set(scheduler.scheduleAtFixedRate(()->{
			SynchronizingFilesBackedValue<?> deref = selfRef.get();
			if(deref==null || deref.isDestroyed())
				job.get().cancel(false);
			else
				deref.pollOnce();
		}, (int) (Math.random()*periodMillis), periodMillis, TimeUnit.MILLISECONDS));
		return job.get();
	}


	private boolean _read() {
		if(isDestroyed())
			throw new IllegalStateException("Value is destroyed");
		Path nf;
		FileTime nfTime=null;
		retry:while(true) {
			initializeInstead:{
				read:synchronized(this) {
					List<Path> fs = file.get();
					nf = newest(fs, f->Files.exists(f) && f.toFile().canRead());	

					if(nf==null) {
						break read;
					}		

					try {
						if(timestamp >= (nfTime=Files.getLastModifiedTime(nf)).toMillis() && size == Files.size(nf))
							return false;
					} catch (IOException e) {
						logger.log(Level.WARNING, "Error reading metadata of backing file "+nf, e);
					}
					Lock ll = canonicalPathLock(nf);
					ll.lock();
					try(FileChannel c = FileChannel.open(nf, StandardOpenOption.READ, StandardOpenOption.WRITE);
							FileLock l = c.lock(0, Long.MAX_VALUE, false)) {
						Path nf2 = newest(fs, f->Files.exists(f) && f.toFile().canRead());	
						if(nf!=nf2)
							continue retry;
						
						T oldValue = currentValue;

						currentValue = codec.decode(nf);

						if(defaultValue==null && equivalence.test(oldValue, currentValue))
							return false;
						defaultValue = null;
						FileTime time = Files.getLastModifiedTime(nf);
						timestamp = time.toMillis();
						logger.log(Level.INFO, dependencyName()+" read new value from backing file "+nf+": "+currentValue+" @ "+time);
						size = Files.size(nf);

					} catch (IOException e) {
						logger.log(Level.WARNING, "Error reading backing file "+nf, e);
					}finally {
						ll.unlock();
					}
					notifyAll();

					break initializeInstead;
				}
				synchronized (this) {					
					if(defaultValue!=null) {
						currentValue = defaultValue.get();
						defaultValue = null;
						timestamp = 0;
					}
				}
				return _write(currentValue, true, null, FileTime.fromMillis(timestamp), false, false);
			}
			_write(currentValue, true, nf, nfTime, false, false);
			return true;
		}

	}


	public void pollOnce() {
		read();
		_write(currentValue, true, null, null, true, true);
	}
	@Override
	public boolean willNeverChange() {
		return false;
	}
	String name;
	public SynchronizingFilesBackedValue<T> name(String name) {
		this.name = name;
		return this;
	}
	@Override
	public String dependencyName() {
		return name==null?"<"+file.toString()+">":name;
	}
	@Override
	public void await(WaitService ws, BooleanSupplier c) throws InterruptedException {
		while(!c.getAsBoolean()) {
			synchronized (this) {
				ws.wait(this, 1000);
			}
		}
	}
	@Override
	public boolean await(WaitService ws, BooleanSupplier c, long millis) throws InterruptedException {
		long t0 = System.currentTimeMillis();
		while(!c.getAsBoolean()) {
			synchronized (this) {
				long left = millis - (System.currentTimeMillis()-t0);
				if(left<=0)
					return false;
				ws.wait(this, Math.min(1000, left));
			}
		}
		return true;
	}

	@Override
	public BiPredicate<? super T, ? super T> _getEquivalence() {
		return equivalence;
	}
	@Override
	public boolean isInTransaction() {
		return false;
	}
	@Override
	public ReadListenDependencyBool inTransactionValue() {
		return Piles.FALSE;
	}
	volatile boolean destroyed = false;
	@Override
	public boolean isDestroyed() {
		return destroyed;
	}
	public void destroy() {
		destroyed = true;
		currentValue=null;
		notifyAll();
	}
	@Override
	public void __beginTransaction(boolean invalidate) {
	}
	@Override
	public void permaInvalidate() {
	}
	@Override
	public void valueMutated() {
		write(currentValue, true);
	}
	@Override
	public void _setEquivalence(BiPredicate<? super T, ? super T> equivalence) {
		this.equivalence = equivalence;
	}
	@Override
	public void revalidate() {		
	}
	@Override
	public void __endTransaction(boolean changedIfOldInvalid) {
	}
	@Override
	public T applyCorrection(T v) {
		return v;
	}
	@Override
	public boolean remembersLastValue() {
		return false;
	}
	@Override
	public void storeLastValueNow() {		
	}
	@Override
	public void resetToLastValue() {		
	}

	@Override
	public ListenerManager _getListenerManager() {
		if(manager == null) {
			synchronized (this) {
				if(manager == null)
					manager = new ListenerManager(this);
			}

		}
		return manager;
	}

	@Override
	public Suppressor suppressRememberLastValue() {
		return Suppressor.NOP;
	}
	public void fireValueChange() {
		if(isDestroyed())
			throw new IllegalStateException("Value is destroyed");
		if(manager!=null)
			manager.fireValueChange();
	}

	@Override
	public String toString() {
		return dependencyName();
	}



	static final WeakHashMap<Path, WeakReference<Path>> canonicalPathInstances = new WeakHashMap<>();
	static final WeakHashMap<Path, Lock> canonicalPathLocks = new WeakHashMap<>();
	public synchronized static Path canonicalPathInstance(Path p) {
		WeakReference<Path> ref = canonicalPathInstances.get(p);
		if(ref!=null)
			return ref.get();
		canonicalPathInstances.put(p, new WeakReference<>(p));
		return p;
	}
	public synchronized static Lock canonicalPathLock(Path id) {
		id = canonicalPathInstance(id);
		Lock lock = canonicalPathLocks.computeIfAbsent(id, k->new ReentrantLock());
		return lock;
	}
}
