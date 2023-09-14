package pile.utils;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import pile.aspect.suppress.MockBlock;

/**
 * An object representing a scope that may be entered only once at a time per thread.
 * It prevents reentrant code execution by redirecting
 * would-be reentrant control flow.
 * @author bb
 *
 */
public class Nonreentrant{
	ThreadLocal<Boolean> in=new ThreadLocal<>();
	/**
	 * Enters the scope of this nonreentrant to execute a {@link Supplier}
	 * @param <E>
	 * @param action Normally run this action
	 * @param fail Run this action if this {@link Thread} has already 
	 * entered this {@link Nonreentrant} scope.
	 * @return
	 */
	public <E> E get(Supplier<? extends E> action, Supplier<? extends E> fail) {
		Boolean isIn = in.get();
		try {
			if(Boolean.TRUE.equals(isIn))
				return fail.get();
			in.set(Boolean.TRUE);
			return action.get();
		}finally {
			in.set(isIn);
		}
	}
	/**
	 * Enters the scope of this nonreentrant to execute a {@link Function}
	 * @param <A>
	 * @param <R>
	 * @param argument the argument to the function
	 * @param action Normally run this action
	 * @param fail Run this action if this {@link Thread} 
	 * has already entered this {@link Nonreentrant} scope.
	 
	 * @return
	 */
	public <A, R> R apply(A argument, Function<? super A, ? extends R> action, 
			Function<? super A, ? extends R> fail) {
		Boolean isIn = in.get();
		try {
			if(Boolean.TRUE.equals(isIn))
				return fail.apply(argument);
			in.set(Boolean.TRUE);
			return action.apply(argument);
		}finally {
			in.set(isIn);
		}
	}
	/**
	 * Enters the scope of this nonreentrant to execute a {@link Consumer}
	 * @param <A>
	 * @param argument the argument to the consumer
	 * @param action Normally run this action
	 * @param fail Run this action if this {@link Thread} 
	 * has already entered this {@link Nonreentrant} scope.
	 */
	public <A> void accept(A argument, 
			Consumer<? super A> action, 
			Consumer<? super A> fail) {
		Boolean isIn = in.get();
		try {
			if(Boolean.TRUE.equals(isIn)) {
				fail.accept(argument);
				return;
			}
			in.set(Boolean.TRUE);
			action.accept(argument);
		}finally {
			in.set(isIn);
		}
	}

	/**
	 * Enters the scope of this nonreentrant to execute a {@link Runnable}
	 * @param action Normally run this action
	 * @param fail Run this action if this {@link Thread} 
	 * has already entered this {@link Nonreentrant} scope.
	 */
	public void run(Runnable action, Runnable fail) {
		Boolean isIn = in.get();
		try {
			if(Boolean.TRUE.equals(isIn)) {
				if(fail!=null)
					fail.run();
				return;
			}
			in.set(Boolean.TRUE);
			action.run();
		}finally {
			in.set(isIn);
		}
	}
	/**
	 * Make a {@link Runnable} that calls {@link #run(Runnable, Runnable)}
	 * @param action
	 * @param fail
	 * @return
	 */
	public Runnable fixed(Runnable action, Runnable fail) {
		return ()->run(action, fail);
	}
	/**
	 * Make a {@link Supplier} that calls {@link #get(Supplier, Supplier)}
	 * @param <E>
	 * @param action
	 * @param fail
	 * @return
	 */
	public <E> Supplier<E> fixed(Supplier<? extends E> action, Supplier<? extends E> fail) {
		return ()->get(action, fail);
	}	
	/**
	 * Make a {@link Function} that calls {@link #apply(Object, Function, Function)}
	 * @param <A>
	 * @param <R>
	 * @param action
	 * @param fail
	 * @return
	 */
	public <A, R> Function<A, R> fixed(
			Function<? super A, ? extends R> action, 
			Function<? super A, ? extends R> fail) {
		return argument->apply(argument, action, fail);
	}
	/**
	 * Make a Consumer that calls {@link #accept(Object, Consumer, Consumer)}
	 * @param <A>
	 * @param action
	 * @param fail
	 * @return
	 */
	public <A> Consumer<A> fixed(
			Consumer<? super A> action, 
			Consumer<? super A> fail) {
		return argument->accept(argument, action, fail);
	}
	/**
	 * 
	 * @return Whether the current {@link Thread} is inside this {@link Nonreentrant} scope
	 */
	public boolean isIn() {
		return Boolean.TRUE.equals(in.get());
	}
	public final class ReentrantException extends Exception {
		private ReentrantException() {
		}
		/**
		 * 
		 * @return The {@link Nonreentrant} scope that caused this Exception
		 */
		public Nonreentrant getScope() {
			return Nonreentrant.this;
		}
	}
	/**
	 * Enter the scope of this {@link Nonreentrant} for the lifetime of the returned
	 * {@link MockBlock}. If the current {@link Thread} is already inside this
	 * scope, a {@link ReentrantException} will be thrown.
	 * @return
	 * @throws ReentrantException
	 */
	public MockBlock block() throws ReentrantException {
		Boolean isIn = in.get();
		if(isIn)
			throw new ReentrantException();
		MockBlock block = MockBlock.closeOnly(()->in.set(false));
		boolean success = false;
		try {
			in.set(Boolean.TRUE);
			success = true; 
			return block;
		}finally {
			if(!success)
				in.set(false);
		}
	}
}
