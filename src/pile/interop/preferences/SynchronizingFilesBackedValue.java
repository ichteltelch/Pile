package pile.interop.preferences;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.ref.WeakReference;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
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

//TODO: use separate locking file
public class SynchronizingFilesBackedValue<T> implements 
ReadWriteListenValue<T>, 
ListenValue.Managed, 
AlwaysValid<T>
{
	private static final Logger logger = Logger.getLogger("SynchronizingFilesBackedValue");

	public static interface FileCodec<T> {
		public void encode(T value, Path path, OutputStream useThis) throws IOException;
		public T decode(Path path, InputStream useThis) throws IOException;
	}
	public static FileCodec<String> STRING_CODEC = new FileCodec<String>() {

		@Override
		public void encode(String value, Path path, OutputStream useThis) throws FileNotFoundException, IOException {
			if(value==null)
				return;
			try(
					OutputStream os = useThis==null?Files.newOutputStream(path):useThis;
					OutputStreamWriter osw = new OutputStreamWriter(useThis, StandardCharsets.UTF_8);
					BufferedWriter bw = new BufferedWriter(osw);
					) 
			{
				bw.write(value);
			}
		}

		@Override
		public String decode(Path path, InputStream useThis) throws FileNotFoundException, IOException {
			try(
					InputStream is = useThis==null?Files.newInputStream(path):useThis;
					InputStreamReader isr = new InputStreamReader(useThis, StandardCharsets.UTF_8);
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
			public void encode(T value, Path path, OutputStream useThis) throws IOException {
				STRING_CODEC.encode(encode.apply(value), path, useThis);
			}

			@Override
			public T decode(Path path, InputStream useThis) throws IOException {
				return encode.applyInverse(STRING_CODEC.decode(path, useThis));
			}
		};
	}

	ReadListenDependency<? extends List<Path>> files;
	FileCodec<T> codec;

	private final Supplier<? extends T> defaultValue;
	boolean initialized;
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
	String tmpFileExtension = ".tmp";
	String lockFileExtension = ".lock";
	ValueListener fileListener = e->{
		timestamp = -100000;
		size=-2;
		read();
	};
	static void ensureLockFileExists(Path mainFile, String lockFileExtension){
		Path lockFile = lockFileExtension==null?mainFile:mainFile.getParent().resolve(mainFile.getFileName()+lockFileExtension);
		if(Files.exists(lockFile))
			return;
		try(OutputStream os = Files.newOutputStream(lockFile)){} catch (IOException e) {
			logger.log(Level.WARNING, "Unable to create lock file: "+lockFile, e);
		}
	}
	ValueListener fileListenerWeakHandle;
	volatile ListenValue.ListenerManager manager;
	public SynchronizingFilesBackedValue(
			ReadListenDependency<? extends Collection<? extends Path>> file, 
					FileCodec<T> codec, 
					Supplier<? extends T> defaultValue) {
		this.codec = codec;
		this.files = file.<List<Path>>map(SynchronizingFilesBackedValue::canonicalize, b->b.corrector(this::restoreFromTmpFiles));
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
	public synchronized void reset() {
		List<Path> paths = files.get();
		initialized = false;
		timestamp = -10000;
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

			if(!force && initialized && equivalence.test(value, currentValue))
				return false;

			List<Path> fs = files.get();
			allExist:if(onlyIfNotExists) {
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
			initialized = true;


			int code;
			try {
				//				HashMap<Path, OutputStream> oss=new HashMap<Path, OutputStream>();
				code = lockingAll(fs.iterator(), spare, tmpFileExtension, lockFileExtension, false, ()->{
					Path f = newest(fs, consider);

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
							codec.encode(currentValue, p, null);
							if(blacklist!=null)
								blacklist.remove(p);
							logger.log(Level.INFO, dependencyName()+" wrote value to backing file "+p+": "+currentValue+ " @ time "+time);
							//							maxTime = Math.max(maxTime, Files.getLastModifiedTime(p).toMillis());
							size = Files.size(p);
							Files.setLastModifiedTime(p, time);
						} catch (IOException e) {
							logger.log(Level.WARNING, "I/O Error writing to backing file "+f, e);
						} catch (RuntimeException e) {
							logger.log(Level.WARNING, "Runtime Error writing to backing file "+f, e);
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
				return code==1 && initialized && equivalence.test(value, currentValue);
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

	private static <T> T lockingAll(Iterator<Path> iterator, Path spare, String tmpFileExtension, String lockFileExtension, boolean shared, Callable<T> action) throws Exception {
		if(iterator.hasNext()) {
			Path next = iterator.next();
			if(next.equals(spare) || !Files.exists(next)) {
				return lockingAll(iterator, spare, tmpFileExtension, lockFileExtension, shared, action);
			}
			boolean succesfullyLocked=false;
			Lock ll = canonicalPathLock(next);
			ll.lock();
			try {

				Path tmp = null;
				boolean needsRestore = false;
				try {
					if(tmpFileExtension!=null) {
						Path parent = next.getParent();
						Path name = next.getFileName();
						String nameTmp = name+tmpFileExtension;
						tmp = parent.resolve(nameTmp);
						Path tmptmp = parent.resolve(nameTmp+tmpFileExtension);
						Files.copy(next, tmptmp, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
						Files.move(tmptmp, tmp, StandardCopyOption.REPLACE_EXISTING);
						needsRestore = true;
					}
					Path lockFile = next.getParent().resolve(next.getFileName()+lockFileExtension);
					ensureLockFileExists(lockFile, null);
					try(
							FileChannel c = FileChannel.open(lockFile, shared? StandardOpenOption.READ: StandardOpenOption.WRITE);
							FileLock l = c.lock(0, Long.MAX_VALUE, shared);
							){
						succesfullyLocked = true;
						T ret = lockingAll(iterator, spare, tmpFileExtension, lockFileExtension, shared, action);
						needsRestore = false;
						return ret;
					}catch (IOException e) {
						if(!succesfullyLocked) {
							logger.log(Level.WARNING, "Error locking backing file "+next, e);
							return lockingAll(iterator, spare, tmpFileExtension, lockFileExtension, shared, action);
						}else {
							throw e;
						}
					}
				}finally {
					try {
						if(needsRestore) {
							Files.copy(tmp, next, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
						}
					}finally {
						if(tmp!=null) {
							Files.delete(tmp);
						}
					}
				}

			}finally {
				ll.unlock();
			}

		}else {
			return action.call();
		}
	}

	public Future<?> autoPoll(ScheduledExecutorService scheduler, long period) {
		return autoPoll(this::pollOnce, Functional.not(this::isDestroyed), scheduler, period);
	}

	public static Future<?> autoPoll(Runnable poll, BooleanSupplier whileTrue, ScheduledExecutorService scheduler, long periodMillis) {
		EarlyMutRef<Future<?>> job = new EarlyMutRef<>();
		WeakCleanupWithRunnable<BooleanSupplier> condRef = whileTrue==null?null:new WeakCleanupWithRunnable<>(whileTrue, ()->job.get().cancel(false));
		WeakCleanupWithRunnable<Runnable> pollfRef = new WeakCleanupWithRunnable<>(poll, ()->job.get().cancel(false));
		job.set(scheduler.scheduleAtFixedRate(()->{
			if(condRef!=null) {
				BooleanSupplier deref = condRef.get();
				if(deref==null || !deref.getAsBoolean()) {
					job.get().cancel(false);
					return;
				}
			}
			Runnable deref = pollfRef.get();
			if(pollfRef==null)
				job.get().cancel(false);
			else
				deref.run();
		}, (int) (Math.random()*periodMillis), periodMillis, TimeUnit.MILLISECONDS));
		return job.get();
	}

	HashSet<Path> blacklist = null;
	Predicate<? super Path> consider = f->Files.exists(f) && f.toFile().canRead();

	List<Path> restoreFromTmpFiles(List<Path> list){
		if(tmpFileExtension!=null) {
			for(Path nf: list) {
				Lock ll = canonicalPathLock(nf);
				ll.lock();
				try {
					Path lockFile = nf.getParent().resolve(nf.getFileName()+lockFileExtension);
					ensureLockFileExists(lockFile, null);
					try(
							FileChannel c = FileChannel.open(lockFile, StandardOpenOption.WRITE);
							FileLock l = c.lock(0, Long.MAX_VALUE, false);
							){
						Path tmp = nf.getParent().resolve(nf.getFileName()+tmpFileExtension);
						if(Files.exists(tmp)) {
							//The tmp file had been completely copied, otherwise it would be the tmptmp file
							Files.copy(tmp, nf, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
							Files.delete(tmp);
						}

					} catch (IOException e) {
						logger.log(Level.WARNING, "Error restoring uncorrupted file "+nf, e);
					}
				}finally {
					ll.unlock();
				}
			}
		}
		return list;
	}

	private boolean _read() {
		if(isDestroyed())
			throw new IllegalStateException("Value is destroyed");
		Path nf;
		FileTime nfTime=null;

		retry:while(true) {

			initializeInstead:{
				read:synchronized(this) {
					List<Path> fs = files.get();

					nf = newest(fs, consider);	

					if(nf==null) {
						break read;
					}		


					Lock ll = canonicalPathLock(nf);
					ll.lock();
					try {

						Path lockFile = nf.getParent().resolve(nf.getFileName()+lockFileExtension);
						ensureLockFileExists(lockFile, null);
						try(
								FileChannel c = FileChannel.open(lockFile, StandardOpenOption.READ);
								FileLock l = c.lock(0, Long.MAX_VALUE, true);
								){
							try {
								if(timestamp >= (nfTime=Files.getLastModifiedTime(nf)).toMillis() && size == Files.size(nf))
									return false;
							} catch (IOException e) {
								logger.log(Level.WARNING, "Error reading metadata of backing file "+nf, e);
							}
							Path nf2 = newest(fs, consider);	
							if(nf!=nf2)
								continue retry;

							T oldValue = currentValue;

							currentValue = codec.decode(nf, null);

							if(initialized && equivalence.test(oldValue, currentValue))
								return false;
							initialized = true;
							FileTime time = Files.getLastModifiedTime(nf);
							timestamp = time.toMillis();
							logger.log(Level.INFO, dependencyName()+" read new value from backing file "+nf+": "+currentValue+" @ "+time);
							size = Files.size(nf);
							notifyAll();

							break initializeInstead;
						} catch (IOException e) {
							logger.log(Level.WARNING, "I/O Error reading backing file "+nf, e);
						} catch (RuntimeException e) {
							logger.log(Level.WARNING, "Runtime Error reading backing file "+nf, e);
						}

					}finally {
						ll.unlock();
					}

					if(blacklist==null) {
						blacklist = new HashSet<>();
						final HashSet<Path> fblacklist = blacklist;
						consider = f->Files.exists(f) && f.toFile().canRead() && (fblacklist==null ||!fblacklist.contains(f));
					}
					blacklist.add(nf);
					continue retry;

				}
				synchronized (this) {					
					if(!initialized) {
						currentValue = defaultValue.get();
						initialized = true;
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
		return name==null?"<"+files.toString()+">":name;
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
