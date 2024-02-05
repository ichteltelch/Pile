package pile.specialized_bool;

import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

import pile.aspect.Dependency;
import pile.aspect.Depender;
import pile.aspect.ReadValue;
import pile.aspect.ValueBracket;
import pile.aspect.WriteValue;
import pile.aspect.combinations.Pile;
import pile.aspect.combinations.ReadDependency;
import pile.aspect.combinations.ReadListenDependency;
import pile.aspect.combinations.ReadWriteDependency;
import pile.builder.FulfillInvalid;
import pile.builder.IBuilder;
import pile.builder.IndependentBuilder;
import pile.builder.PileBuilder;
import pile.builder.SealPileBuilder;
import pile.impl.Independent;
import pile.impl.MutRef;
import pile.impl.PileImpl;
import pile.impl.Piles;
import pile.impl.Piles.AggregationMonoid;
import pile.impl.SealPile;
import pile.interop.exec.StandardExecutors;
import pile.specialized_bool.combinations.ReadDependencyBool;
import pile.specialized_bool.combinations.ReadListenDependencyBool;
import pile.specialized_bool.combinations.ReadValueBool;
import pile.specialized_bool.combinations.ReadWriteListenDependencyBool;
import pile.specialized_double.combinations.ReadValueDouble;

public interface PileBool 
extends Depender, ReadWriteListenDependencyBool, Pile<Boolean>{
	public static boolean DEBUG = false;

	/**
	 * Make an inverted reactive boolean. Writing to the returned value will
	 * result in writing the inverse to {@code input}
	 * @param input
	 * @return
	 */
	public static SealBool not(ReadWriteDependency<Boolean> input) {
		return notRW(input);
	}
	/**
	 * Make and inverted reactive boolean.
	 * @param input
	 * @return
	 */
	public static SealBool not(ReadDependency<? extends Boolean> input) {
		return notRO(input);
	}
	/**
	 * Make an inverted reactive boolean. Writing to the returned value will
	 * result in writing the inverse to {@code input}
	 * @param input
	 * @return
	 */
	public static SealBool notRW(ReadWriteDependency<Boolean> input) {
		String inputName = input.dependencyName();
		return new SealPileBuilder<SealBool, Boolean>(new SealBool())
				.recompute(reco->{
					Boolean v = input.get();
					if(v==null)
						reco.fulfill(null);
					else
						reco.fulfill(!v);

				})
				.seal(v->{
					if(v==null)
						input.set(null);
					else if(!input._getEquivalence().test(input.get(), !v))
						input.set(!v);
				})
				.name(inputName==null?"! ?":("! ("+inputName+")"))
				.whenChanged(input);
	}
	/**
	 * Make and inverted reactive boolean.
	 * @param input
	 * @return
	 */
	public static SealBool notRO(ReadDependency<? extends Boolean> input) {
		String inputName = input.dependencyName();
		return new SealPileBuilder<SealBool, Boolean>(new SealBool())
				.recompute(reco->{
					Boolean v = input.get();
					if(v==null)
						reco.fulfill(null);
					else
						reco.fulfill(!v);

				})
				.seal()
				.name(inputName==null?"! ?":("! ("+inputName+")"))
				.whenChanged(input);

	}
	/**
	 * Combine two booleans according to the following table
	 * <pre>
	 *   I F N T
	 * I I I I I  
	 * F F F F F
	 * N N N N N 
	 * T I F N T
	 * </pre>
	 * @param op1
	 * @param op2
	 * @return
	 */
	public static SealBool andScd(ReadDependency<? extends Boolean> op1, ReadDependency<? extends Boolean> op2) {
		String op1Name = op1.dependencyName();
		String op2Name = op2.dependencyName();
		String name = "("+(op1Name==null?"?":op1Name)+" &&> " + (op2Name==null?"?":op2Name)+")";
		if(op1==op2) {
			return new SealPileBuilder<>(new SealBool())
					.recompute(op1::get)
					.seal()
					.name(name)
					.whenChanged(op1);
		}
		ReadListenDependencyBool v1 = op1.validity();
		ReadListenDependencyBool v2 = op2.validity();
		return new SealPileBuilder<>(new SealBool())
				.recompute(reco->{
					if(v1.isFalse()) {reco.fulfillInvalid(); return;}
					if(!Boolean.TRUE.equals(op1.get())) {reco.fulfill(op1.get()); }
					if(v2.isFalse()) {reco.fulfillInvalid(); return;}
					{reco.fulfill(op2.get()); }
				})
				.name(name)
				.seal()
				.essential(v1, v2, op1, op2)
				.mayNotRemoveDynamicDependency(v1, v2)
				.dynamicDependencies()
				.build();
	}
	/**
	 * Combine two booleans according to the following table
	 * <pre>
	 *   I N F T
	 * I I I I I  
	 * N I N N N 
	 * F I N F F
	 * T I N F T
	 * </pre>
	 * @param op1
	 * @param op2
	 * @return
	 */
	public static SealBool and(ReadDependency<? extends Boolean> op1, ReadDependency<? extends Boolean> op2) {
		String op1Name = op1.dependencyName();
		String op2Name = op2.dependencyName();
		String name = "("+(op1Name==null?"?":op1Name)+" & " + (op2Name==null?"?":op2Name)+")";
		if(op1==op2) {
			return new SealPileBuilder<>(new SealBool())
					.recompute(op1::get)
					.seal()
					.name(name)
					.whenChanged(op1);
		}
		return new SealPileBuilder<>(new SealBool())
				.recompute(reco->{
					Boolean o1 = op1.get();
					Boolean o2 = op2.get();


					if(o1 == null || o2 == null) {
						reco.fulfill(null);
					}else {
						reco.fulfill(o1 & o2);
					}
				})
				.name(name)
				.seal()
				.dependOn(true, op1, op2)
				.build();
	}
	/**
	 * Combine two booleans according to the following table
	 * <pre>
	 *   I F N T
	 * I I I I I  
	 * F I F F F 
	 * N I F N N
	 * T I F N T
	 * </pre>
	 * @param op1
	 * @param op2
	 * @return
	 */
	public static SealBool and2(ReadDependency<? extends Boolean> op1, ReadDependency<? extends Boolean> op2) {
		String op1Name = op1.dependencyName();
		String op2Name = op2.dependencyName();
		String name = "("+(op1Name==null?"?":op1Name)+" && " + (op2Name==null?"?":op2Name)+")";
		if(op1==op2) {
			return new SealPileBuilder<>(new SealBool())
					.recompute(op1::get)
					.seal()
					.name(name)
					.whenChanged(op1);
		}
		return new SealPileBuilder<>(new SealBool())
				.recompute(reco->{
					Boolean o1 = op1.get();
					Boolean o2 = op2.get();

					if((o1!=null && !o1) || (o2!=null && !o2)) {
						reco.fulfill(Boolean.FALSE);
					}else if(o1 == null || o2 == null) {
						reco.fulfill(null);
					}else {
						reco.fulfill(Boolean.TRUE);
					}
				})
				.name(name)
				.seal()
				.dependOn(true, op1, op2)
				.build();
	}
	/**
	 * Combine two booleans according to the following table
	 * <pre>
	 *   N F I T
	 * N N N N N  
	 * F N F F F 
	 * I N F I I
	 * T N F I T
	 * </pre>
	 * @param op1
	 * @param op2
	 * @return a sealed SealableBool
	 */
	public static SealBool and3(ReadListenDependency<? extends Boolean> op1, ReadListenDependency<? extends Boolean> op2) {
		String op1Name = op1.dependencyName();
		String op2Name = op2.dependencyName();
		String name = "("+(op1Name==null?"?":op1Name)+" &&& " + (op2Name==null?"?":op2Name)+")";
		if(op1==op2) {
			return new SealPileBuilder<>(new SealBool())
					.recompute(op1::get)
					.seal()
					.name(name)
					.whenChanged(op1);
		}
		ReadDependency<? extends Boolean> op1Vb=op1.validBuffer_memo();
		ReadDependency<? extends Boolean> op2Vb=op2.validBuffer_memo();
		ReadDependencyBool op1Val=op1.validity();
		ReadDependencyBool op2Val=op2.validity();
		return new SealPileBuilder<>(new SealBool())
				.recompute(reco->{
					boolean o1Valid = op1Val.get();
					boolean o2Valid = op2Val.get();
					Boolean o1 = o1Valid?op1Vb.get():null;
					Boolean o2 = o2Valid?op2Vb.get():null;
					if(reco.isFinished())
						return;

					if(o1Valid && o1==null) {

						reco.fulfill(null, ()->{
							reco.addDependency(op1Val);
							reco.addDependency(op1Vb);
							reco.removeDependency(op2Val);
							reco.removeDependency(op2Vb);
						});
						return;
					}
					if(o2Valid && o2==null) {
						reco.fulfill(null, ()->{
							reco.addDependency(op2Val);
							reco.addDependency(op2Vb);
							reco.removeDependency(op1Val);
							reco.removeDependency(op1Vb);					
						});
						return;
					}
					Runnable dc = ()->{
						reco.addDependency(op1Val);
						reco.addDependency(op1Vb);
						reco.addDependency(op2Val);
						reco.addDependency(op2Vb);
					};
					if(o1Valid && !o1) {
						reco.fulfill(o1, dc);
					}else if(o2Valid && !o2) {
						reco.fulfill(o2, dc);
					}else if(!o1Valid || !o2Valid) {
						System.err.println("&&&: "+"both invalid");
						reco.fulfillInvalid(dc);
					}else {
						reco.fulfill(Boolean.TRUE, dc);
					}
				})
				.name(name)
				.seal()
				.dependOn(false, op1Val, op2Val, op1Vb, op2Vb)
				.build();
	}

	/**
	 * Combine two booleans according to the following table
	 * <pre>
	 *   I T N F
	 * I I I I I  
	 * T T T T T
	 * N N N N N 
	 * F I T N F
	 * </pre>
	 * @param op1
	 * @param op2
	 * @return
	 */
	public static SealBool orScd(ReadDependency<? extends Boolean> op1, ReadDependency<? extends Boolean> op2) {
		String op1Name = op1.dependencyName();
		String op2Name = op2.dependencyName();
		String name = "("+(op1Name==null?"?":op1Name)+" ||> " + (op2Name==null?"?":op2Name)+")";
		if(op1==op2) {
			return new SealPileBuilder<>(new SealBool())
					.recompute(op1::get)
					.seal()
					.name(name)
					.whenChanged(op1);
		}
		ReadListenDependencyBool v1 = op1.validity();
		ReadListenDependencyBool v2 = op2.validity();
		return new SealPileBuilder<>(new SealBool())
				.recompute(reco->{
					if(v1.isFalse()) {reco.fulfillInvalid(); return;}
					if(!Boolean.FALSE.equals(op1.get())) {reco.fulfill(op1.get()); }
					if(v2.isFalse()) {reco.fulfillInvalid(); return;}
					{reco.fulfill(op2.get()); }
				})
				.name(name)
				.seal()
				.essential(v1, v2, op1, op2)
				.mayNotRemoveDynamicDependency(v1, v2)
				.dynamicDependencies()
				.build();
	}
	/**
	 * Combine two booleans according to the following table
	 * <pre>
	 *   I N T F
	 * I I I I I  
	 * N I N N N 
	 * T I N T T
	 * F I N T F
	 * </pre>
	 * @param op1
	 * @param op2
	 * @return
	 */
	public static SealBool or(ReadDependency<? extends Boolean> op1, ReadDependency<? extends Boolean> op2) {
		String op1Name = op1.dependencyName();
		String op2Name = op2.dependencyName();
		String name = "("+(op1Name==null?"?":op1Name)+" | " + (op2Name==null?"?":op2Name)+")";
		if(op1==op2) {
			return new SealPileBuilder<>(new SealBool())
					.recompute(op1::get)
					.seal()
					.name(name)
					.whenChanged(op1);
		}
		return new SealPileBuilder<>(new SealBool())
				.recompute(reco->{
					Boolean o1 = op1.get();
					Boolean o2 = op2.get();


					if(o1 == null || o2 == null) {
						reco.fulfill(null);
					}else {
						reco.fulfill(o1 | o2);
					}
				})
				.name(name)
				.seal()
				.dependOn(true, op1, op2)
				.build();
	}
	/**
	 * Combine two booleans according to the following table
	 * <pre>
	 *   I T N F
	 * I I I I I  
	 * T I T T T 
	 * N I T N N
	 * F I T N F
	 * </pre>
	 * @param op1
	 * @param op2
	 * @return
	 */
	public static SealBool or2(ReadDependency<? extends Boolean> op1, ReadDependency<? extends Boolean> op2) {
		String op1Name = op1.dependencyName();
		String op2Name = op2.dependencyName();
		String name = "("+(op1Name==null?"?":op1Name)+" || " + (op2Name==null?"?":op2Name)+")";
		if(op1==op2) {
			return new SealPileBuilder<>(new SealBool())
					.recompute(op1::get)
					.seal()
					.name(name)
					.whenChanged(op1);
		}
		return new SealPileBuilder<>(new SealBool())
				.recompute(reco->{
					Boolean o1 = op1.get();
					Boolean o2 = op2.get();

					if((o1!=null && o1) || (o2!=null && o2)) {
						reco.fulfill(Boolean.TRUE);
					}else if(o1 == null || o2 == null) {
						reco.fulfill(null);
					}else {
						reco.fulfill(Boolean.FALSE);
					}
				})
				.name(name)
				.seal()
				.dependOn(true, op1, op2)
				.build();
	}
	/**
	 * Combine two booleans according to the following table
	 * <pre>
	 *   N T I F
	 * N N N N N  
	 * T N T T T 
	 * I N T I I
	 * F N T I F
	 * </pre>
	 * @param op1
	 * @param op2
	 * @return a sealed SealableBool
	 */
	public static SealBool or3(ReadListenDependency<? extends Boolean> op1, ReadListenDependency<? extends Boolean> op2) {
		String op1Name = op1.dependencyName();
		String op2Name = op2.dependencyName();
		String name = "("+(op1Name==null?"?":op1Name)+" ||| " + (op2Name==null?"?":op2Name)+")";
		if(op1==op2) {
			return new SealPileBuilder<>(new SealBool())
					.recompute(op1::get)
					.seal()
					.name(name)
					.whenChanged(op1);
		}
		ReadDependency<? extends Boolean> op1Vb=op1.validBuffer_memo();
		ReadDependency<? extends Boolean> op2Vb=op2.validBuffer_memo();
		ReadDependencyBool op1Val=op1.validity();
		ReadDependencyBool op2Val=op2.validity();

		return new SealPileBuilder<>(new SealBool())
				.recompute(reco->{
					boolean o1Valid = op1Val.get();
					boolean o2Valid = op2Val.get();
					Boolean o1 = o1Valid?op1Vb.get():null;
					Boolean o2 = o2Valid?op2Vb.get():null;

					if(o1Valid && o1==null) {

						reco.fulfill(null, ()->{
							reco.addDependency(op1Val);
							reco.addDependency(op1Vb);
							reco.removeDependency(op2Val);
							reco.removeDependency(op2Vb);
						});
						return;
					}
					if(o2Valid && o2==null) {

						reco.fulfill(null, ()->{
							reco.addDependency(op2Val);
							reco.addDependency(op2Vb);
							reco.removeDependency(op1Val);
							reco.removeDependency(op1Vb);
						});
						return;
					}
					Runnable dc = ()->{
						reco.addDependency(op1Val);
						reco.addDependency(op1Vb);
						reco.addDependency(op2Val);
						reco.addDependency(op2Vb);
					};
					if(o1Valid && o1) {
						reco.fulfill(o1, dc);
					}else if(o2Valid && o2) {
						reco.fulfill(o2, dc);
					}else if(!o1Valid || !o2Valid) {
						reco.fulfillInvalid(dc);
					}else {
						reco.fulfill(Boolean.FALSE, dc);
					}
				})
				.name(name)
				.seal()
				.dependOn(false, op1, op2)
				.build();
	}
	/**
	 * Delegates to {@link #_chooseConst(ReadDependency, Object, Object, Object, SealPile)}
	 */
	public static <E> SealPile<E> chooseConst(ReadDependency<? extends Boolean> chooser, E ifTrue, E ifFalse, E ifNull){
		return _chooseConst(chooser, ifTrue, ifFalse, ifNull, new SealPile<E>());
	}
	/**
	 * Delegates to {@link #_chooseConst(ReadDependency, Object, Object, Object, SealPile)}
	 */
	public static SealBool chooseConst(ReadDependency<? extends Boolean> chooser, Boolean ifTrue, Boolean ifFalse, Boolean ifNull){
		return _chooseConst(chooser, ifTrue, ifFalse, ifNull, new SealBool());
	}
	/**
	 * Delegates to {@link PileBool#_choose(ReadDependency, ReadDependency, ReadDependency, ReadDependency, SealPile)}
	 */
	public static <E> SealPile<E> choose(
			ReadDependency<? extends Boolean> chooser, 
			ReadDependency<? extends E> ifTrue, 
			ReadDependency<? extends E> ifFalse, 
			ReadDependency<? extends E> ifNull){
		return _choose(chooser, ifTrue, ifFalse, ifNull, new SealPile<>());
	}


	/**
	 * Delegates to {@link #_chooseWritable(ReadDependency, ReadWriteDependency, ReadWriteDependency, ReadWriteDependency, SealPile)}
	 */
	public static <E> SealPile<E> chooseWritable(
			ReadDependency<? extends Boolean> chooser, 
			ReadWriteDependency<E> ifTrue, 
			ReadWriteDependency<E> ifFalse, 
			ReadWriteDependency<E> ifNull
			) {
		return _chooseWritable(chooser, ifTrue, ifFalse, ifNull, new SealPile<>());
	}

	/**
	 * Configure the {@code template} to take on the value of one of the
	 * three branches, depending on the value of the {@code chooser} 
	 * @param <E>
	 * @param <V>
	 * @param chooser
	 * @param ifTrue
	 * @param ifFalse
	 * @param ifNull
	 * @param template
	 * @return
	 */
	public static <E, V extends SealPile<E>> V _choose(
			ReadDependency<? extends Boolean> chooser, 
			ReadDependency<? extends E> ifTrue, 
			ReadDependency<? extends E> ifFalse, 
			ReadDependency<? extends E> ifNull,
			V template) {
		if(ifNull==null) {
			return _choose(chooser, ifTrue, ifFalse, Piles.constNull(), template);
		}
		String chooserName = chooser.dependencyName();
		String ifTrueName = ifTrue.dependencyName();
		String ifFalseName = ifFalse.dependencyName();
		String ifNullName = ifNull.dependencyName();
		String name = "(choice on "+(chooserName == null?"?":chooserName)+": ["+
				(ifTrueName==null?"?":ifTrueName)+","+
				(ifFalseName==null?"?":ifFalseName)+","+
				(ifNullName==null?"?":ifNullName)+"])";


		return new SealPileBuilder<>(template)
				.essential(chooser, ifTrue, ifFalse, ifNull)
				.mayNotRemoveDynamicDependency(chooser)
				.recompute(reco->{
					if(!chooser.isValid()) {
						reco.fulfillRetry();
					}
					Boolean choice = chooser.get();
					if(choice==null) {
						reco.fulfill(ifNull.get());
					}else if(choice) {
						reco.fulfill(ifTrue.get());
					}else {
						reco.fulfill(ifFalse.get());
					}

				})
				.dynamicDependencies()
				.name(name)
				.seal()
				.build();
	}
//	static <E, V extends SealPile<E>> V _choose_good(
//			ReadDependency<? extends Boolean> chooser, 
//			ReadDependency<? extends E> ifTrue, 
//			ReadDependency<? extends E> ifFalse, 
//			ReadDependency<? extends E> ifNull,
//			V template) {
//		if(ifNull==null) {
//			return _choose(chooser, ifTrue, ifFalse, Piles.constNull(), template);
//		}
//		String chooserName = chooser.dependencyName();
//		String ifTrueName = ifTrue.dependencyName();
//		String ifFalseName = ifFalse.dependencyName();
//		String ifNullName = ifNull.dependencyName();
//		String name = "(choice on "+(chooserName == null?"?":chooserName)+": ["+
//				(ifTrueName==null?"?":ifTrueName)+","+
//				(ifFalseName==null?"?":ifFalseName)+","+
//				(ifNullName==null?"?":ifNullName)+"])";
//
//		//		ReadDependency<? extends E> trueVb = ifTrue.validBuffer_ro();
//		//		ReadDependency<? extends E> falsVb = ifFalse.validBuffer_ro();
//		//		ReadDependency<? extends E> nullVb = ifNull.validBuffer_ro();
//		//		ReadDependencyBool trueVal = ifTrue.validity();
//		//		ReadDependencyBool falsVal = ifFalse.validity();
//		//		ReadDependencyBool nullVal = ifNull.validity();
//
//		return new SealPileBuilder<>(template)
//				.essential(chooser, ifTrue, ifFalse, ifNull)
//				.mayNotRemoveDynamicDependency(chooser)
//				.recompute(reco->{
//					if(!chooser.isValid()) {
//						reco.fulfillRetry();
//					}
//					Boolean choice = chooser.get();
//					if(choice==null) {
//						//						if(!nullVal.isTrue())
//						//							reco.fulfillInvalid();
//						//						else
//						reco.fulfill(ifNull.get());
//					}else if(choice) {
//						//						if(!trueVal.isTrue())
//						//							reco.fulfillInvalid();
//						//						else
//						reco.fulfill(ifTrue.get());
//					}else {
//						//						if(!falsVal.isTrue())
//						//							reco.fulfillInvalid();
//						//						else
//						reco.fulfill(ifFalse.get());
//					}
//
//				})
//				.dynamicDependencies()
//				.name(name)
//				.seal()
//				.build();
//	}
	//	public static <E, V extends SealPile<E>> V _choose_bad(
	//			ReadDependency<? extends Boolean> chooser, 
	//			ReadListenDependency<? extends E> ifTrue, 
	//			ReadListenDependency<? extends E> ifFalse, 
	//			ReadListenDependency<? extends E> ifNull,
	//			V template) {
	//		if(ifNull==null) {
	//			return _choose(chooser, ifTrue, ifFalse, Piles.constNull(), template);
	//		}
	//		String chooserName = chooser.dependencyName();
	//		String ifTrueName = ifTrue.dependencyName();
	//		String ifFalseName = ifFalse.dependencyName();
	//		String ifNullName = ifNull.dependencyName();
	//		String name = "(choice on "+(chooserName == null?"?":chooserName)+": ["+
	//				(ifTrueName==null?"?":ifTrueName)+","+
	//				(ifFalseName==null?"?":ifFalseName)+","+
	//				(ifNullName==null?"?":ifNullName)+"])";
	//
	//		ReadDependency<? extends E> trueVb = ifTrue.readOnlyValidBuffer();
	//		ReadDependency<? extends E> falsVb = ifFalse.readOnlyValidBuffer();
	//		ReadDependency<? extends E> nullVb = ifNull.readOnlyValidBuffer();
	//		ReadDependencyBool trueVal = ifTrue.validity();
	//		ReadDependencyBool falsVal = ifFalse.validity();
	//		ReadDependencyBool nullVal = ifNull.validity();
	//
	//		return new SealPileBuilder<>(template)
	//				.dependOn(chooser)
	//				.recompute(reco->{
	//					if(!chooser.isValid()) {
	//						reco.fulfillRetry();
	//					}
	//					Boolean choice = chooser.get();
	//					if(choice==null) {
	//						if(!nullVal.isTrue())
	//							reco.fulfillInvalid();
	//						else
	//							reco.fulfill(nullVb.get());
	//					}else if(choice) {
	//						if(!trueVal.isTrue())
	//							reco.fulfillInvalid();
	//						else
	//							reco.fulfill(trueVb.get());
	//					}else {
	//						if(!falsVal.isTrue())
	//							reco.fulfillInvalid();
	//						else
	//							reco.fulfill(falsVb.get());
	//					}
	//
	//				})
	//				.dynamicDependencies()
	//				.name(name)
	//				.seal()
	//				.build();
	//	}
	/**
	 * Configure the {@code template} to take on the value of one of the
	 * three branches, depending on the value of the {@code chooser}
	 * <p>
	 * Writes to the {@code template} will be forwarded to the active branch.

	 * @param <E>
	 * @param <V>
	 * @param chooser
	 * @param ifTrue
	 * @param ifFalse
	 * @param ifNull
	 * @param template
	 * @return
	 */
	public static <E, V extends SealPile<E>> V _chooseWritable(
			ReadDependency<? extends Boolean> chooser, 
			ReadWriteDependency<E> ifTrue, 
			ReadWriteDependency<E> ifFalse, 
			ReadWriteDependency<E> ifNull,
			V template) {
		if(ifNull==null) {
			return _chooseWritable(chooser, ifTrue, ifFalse, Piles.constNull(), template);
		}
		String chooserName = chooser.dependencyName();
		String ifTrueName = ifTrue.dependencyName();
		String ifFalseName = ifFalse.dependencyName();
		String ifNullName = ifNull.dependencyName();
		String name = "(choice on "+(chooserName == null?"?":chooserName)+": ["+
				(ifTrueName==null?"?":ifTrueName)+","+
				(ifFalseName==null?"?":ifFalseName)+","+
				(ifNullName==null?"?":ifNullName)+"])";


		return new SealPileBuilder<>(template)
				.essential(chooser, ifTrue, ifFalse, ifNull)
				.mayNotRemoveDynamicDependency(chooser)
				.recompute(reco->{
					if(!chooser.isValid()) {
						reco.fulfillRetry();
					}
					Boolean choice = chooser.get();
					if(choice==null) {
						reco.fulfill(ifNull.get());
					}else if(choice) {
						reco.fulfill(ifTrue.get());
					}else {
						reco.fulfill(ifFalse.get());
					}

				})
				.dynamicDependencies()
				.scoutIfInvalid(ifNull, ifTrue, ifFalse)
				.name(name)
				.seal(v->{
					if(!chooser.isValid()) {
						return;
					}
					Boolean choice = chooser.get();
					WriteValue<? super E> chosen;
					if(choice==null) {
						chosen=ifNull;
					}else if(choice){
						chosen=ifTrue;
					}else {
						chosen=ifFalse;
					}

					chosen.set(v);

				})
				.build();
	}
	/**
	 * Configure the {@code template} to take on the value that is one of the
	 * three branches, depending on the value of the {@code chooser}
	 * @param <E>
	 * @param <V>
	 * @param chooser
	 * @param ifTrue
	 * @param ifFalse
	 * @param ifNull
	 * @param template
	 * @return
	 */
	public static <E, V extends SealPile<E>> V _chooseConst(ReadDependency<? extends Boolean> chooser, E ifTrue, E ifFalse, E ifNull,
			V template) {
		String chooserName = chooser.dependencyName();
		String name = "(choice on "+(chooserName == null?"?":chooserName)+")";

		return new SealPileBuilder<>(template)
				.dependOn(chooser)
				.recompute(()->{
					Boolean b = chooser.get();
					if(b==null)
						return ifNull;
					if(b)
						return ifTrue;
					return ifFalse;
				})
				.name(name)
				.seal()
				.build();
	}

	/** Specialization of {@link Piles#readOnlyWrapperIdempotent(ReadDependency)} */
	public static ReadListenDependencyBool readOnlyWrapperIdempotent(ReadDependency<? extends Boolean> in){
		if(in instanceof SealBool) {
			SealBool cast = (SealBool)in;
			if(cast.isDefaultSealed())
				return cast;
		}else if(in instanceof ConstantBool) {
			ConstantBool cast = (ConstantBool) in;
			return cast;	
		}
		return Piles.makeReadOnlyWrapper(in, new SealBool());
	}
	/** Specialization of {@link Piles#readOnlyWrapper(ReadDependency)} */
	public static SealBool readOnlyWrapper(ReadDependency<? extends Boolean> in){
		return Piles.makeReadOnlyWrapper(in, new SealBool());
	}
	/**
	 * Partly specialized {@link AggregationMonoid} for boolean operations
	 * @author bb
	 *
	 */
	public static class BoolAggregator implements Piles.AggregationMonoid<Boolean, ReadListenDependencyBool>{

		final ReadListenDependencyBool neutral;
		final BiFunction<? super ReadListenDependency<? extends Boolean>, ? super ReadListenDependency<? extends Boolean>, ? extends ReadListenDependencyBool> op;
		public BoolAggregator(Boolean neutral, 
				BiFunction<? super ReadListenDependency<? extends Boolean>, ? super ReadListenDependency<? extends Boolean>, ? extends ReadListenDependencyBool> op) {
			if(neutral==null)
				this.neutral=Piles.NULL_B;
			else if(neutral)
				this.neutral=Piles.TRUE;
			else
				this.neutral=Piles.FALSE;
			this.op=op;
		}
		@Override
		public ReadListenDependencyBool constantNeutral() {
			return neutral;
		}
		@Override
		public ReadListenDependencyBool apply(ReadListenDependency<? extends Boolean> op1, ReadListenDependency<? extends Boolean> op2) {
			return op.apply(op1, op2);
		}

		@Override
		public ReadListenDependencyBool inject(ReadListenDependency<? extends Boolean> o) {
			return readOnlyWrapperIdempotent(o);
		}

	}

	/**
	 * Aggregate some reactive values using the {@link PileBool#and(ReadDependency)} operation.
	 */
	public static ReadListenDependencyBool conjunction(Iterable<? extends ReadListenDependency<? extends Boolean>> items) {
		return Piles.aggregate(Piles.andAggregator, items);
	}
	/**
	 * Aggregate some reactive values using the {@link PileBool#and(ReadDependency)} operation.
	 */
	@SafeVarargs
	public static ReadListenDependencyBool conjunction(ReadListenDependency<? extends Boolean>... items) {
		return Piles.aggregate(Piles.andAggregator, items);
	}
	/**
	 * Aggregate some reactive values using the {@link PileBool#and2(ReadDependency)} operation.
	 */
	public static ReadListenDependencyBool conjunction2(Iterable<? extends ReadListenDependency<? extends Boolean>> items) {
		return Piles.aggregate(Piles.and2Aggregator, items);
	}
	/**
	 * Aggregate some reactive values using the {@link PileBool#and2(ReadDependency)} operation.
	 */
	@SafeVarargs
	public static ReadListenDependencyBool conjunction2(ReadListenDependency<? extends Boolean>... items) {
		return Piles.aggregate(Piles.and2Aggregator, items);
	}
	/**
	 * Aggregate some reactive values using the {@link PileBool#and3(ReadDependency)} operation.
	 */
	public static ReadListenDependencyBool conjunction3(Iterable<? extends ReadListenDependency<? extends Boolean>> items) {
		return Piles.aggregate(Piles.and3Aggregator, items);
	}
	/**
	 * Aggregate some reactive values using the {@link PileBool#and3(ReadDependency)} operation.
	 */	@SafeVarargs
	 public static ReadListenDependencyBool conjunction3(ReadListenDependency<? extends Boolean>... items) {
		 return Piles.aggregate(Piles.and3Aggregator, items);
	 }
	 
	 
	 /**
	  * Aggregate some reactive values using the {@link PileBool#or(ReadDependency)} operation.
	  */
	 public static ReadListenDependencyBool disjunction(Iterable<? extends ReadListenDependency<? extends Boolean>> items) {
		 return Piles.aggregate(Piles.orAggregator, items);
	 }
	 /**
	  * Aggregate some reactive values using the {@link PileBool#or(ReadDependency)} operation.
	  */
	 @SafeVarargs
	 public static ReadListenDependencyBool disjunction(ReadListenDependency<? extends Boolean>... items) {
		 return Piles.aggregate(Piles.orAggregator, items);
	 }
	 /**
	  * Aggregate some reactive values using the {@link PileBool#or2(ReadDependency)} operation.
	  */
	 public static ReadListenDependencyBool disjunction2(Iterable<? extends ReadListenDependency<? extends Boolean>> items) {
		 return Piles.aggregate(Piles.or2Aggregator, items);
	 }
	 /**
	  * Aggregate some reactive values using the {@link PileBool#or2(ReadDependency)} operation.
	  */
	 @SafeVarargs
	 public static ReadListenDependencyBool disjunction2(ReadListenDependency<? extends Boolean>... items) {
		 return Piles.aggregate(Piles.or2Aggregator, items);
	 }
	 /**
	  * Aggregate some reactive values using the {@link PileBool#or3(ReadDependency)} operation.
	  */
	 public static ReadListenDependencyBool disjunction3(Iterable<? extends ReadListenDependency<? extends Boolean>> items) {
		 return Piles.aggregate(Piles.or3Aggregator, items);
	 }
	 /**
	  * Aggregate some reactive values using the {@link PileBool#or3(ReadDependency)} operation.
	  */
	 @SafeVarargs
	 public static ReadListenDependencyBool disjunction3(ReadListenDependency<? extends Boolean>... items) {
		 return Piles.aggregate(Piles.or3Aggregator, items);
	 }

	 /**
	  * Make a reactive boolean that computes itself using the given {@link BiPredicate} applied on two reactive values
	  * @param <O1>
	  * @param <O2>
	  * @param op1
	  * @param op2
	  * @param op
	  * @return
	  */
	 public static <O1, O2> SealBool binOp(
			 ReadDependency<? extends O1> op1, 
			 ReadDependency<? extends O2> op2, 
			 BiPredicate<? super O1, ? super O2> op) {
		 return Piles.makeBinOp(op1, op2, new SealBool(), op::test);
	 }
	 /**
	  * Make a reactive boolean that computes itself using the given {@link Boolean}-valued 
	  * {@link BiFunction} applied on two reactive values
	  * @param <O1>
	  * @param <O2>
	  * @param op1
	  * @param op2
	  * @param op
	  * @return
	  */
	 public static <O1, O2> SealBool binOp(
			 ReadDependency<? extends O1> op1, 
			 ReadDependency<? extends O2> op2, 
			 BiFunction<? super O1, ? super O2, ? extends Boolean> op) {
		 return Piles.makeBinOp(op1, op2, new SealBool(), op);
	 }
	 /**
	  * Make a reactive boolean that computes itself to be the result of comparing two
	  * reactive values using their natural ordering
	  * @param <E>
	  * @param op1
	  * @param op2
	  * @param ifLess Value to take if {@code op1} is less than {@code op2}
	  * @param ifEqual Value to take if {@code op1} is equal to {@code op2}
	  * @param ifGreater Value to take if {@code op1} is greater than {@code op2}
	  * @param nullIsLess Whether a <code>null</code> reference should be 
	  * treated as less than all other values of type {@code E}.
	  * If this Parameter is <code>null</code>, the reactive boolean will take the value <code>null</code>
	  * @return
	  */
	 public static <E extends Comparable<? super E>> 
	 SealBool comparison(
			 ReadDependency<? extends E> op1, 
			 ReadDependency<? extends E> op2,
			 Boolean ifLess, Boolean ifEqual, Boolean ifGreater,
			 Boolean nullIsLess
			 ){
		 return Piles.makeBinOp(op1, op2, new SealBool(), (o1, o2)->{
			 int comp;
			 if(o1==o2) {
				 if(o1==null & nullIsLess==null)
					 return null;			
				 comp=0;
			 }else if(o1==null) {
				 if(nullIsLess==null)
					 return null;
				 comp=nullIsLess?-1:1;
			 }else if(o2==null) {
				 if(nullIsLess==null)
					 return null;
				 comp=nullIsLess?1:-1;
			 }else
				 comp = o1.compareTo(o2);
			 return comp==0?ifEqual:comp<0?ifLess:ifGreater;
		 });
	 }
	 /**
	  * Make a reactive boolean that computes itself to be the result of comparing 
	  * a constant to a reactive value using their natural ordering
	  * @param <E>
	  * @param o1
	  * @param op2
	  * @param ifLess Value to take if {@code op1} is less than {@code op2}
	  * @param ifEqual Value to take if {@code op1} is equal to {@code op2}
	  * @param ifGreater Value to take if {@code op1} is greater than {@code op2}
	  * @param nullIsLess Whether a <code>null</code> reference should be 
	  * treated as less than all other values of type {@code E}.
	  * If this Parameter is <code>null</code>, the reactive boolean will take the value <code>null</code>
	  * @return
	  */
	 public static <E extends Comparable<? super E>> 
	 SealBool comparison(
			 E o1,
			 ReadDependency<? extends E> op2, 
			 Boolean ifLess, Boolean ifEqual, Boolean ifGreater, Boolean nullIsLess){
		 return comparison(op2, o1, ifGreater, ifEqual, ifLess, nullIsLess);
	 }
	 /**
	  * Make a reactive boolean that computes itself to be the result of comparing 
	  * a reactive value to a constant using their natural ordering
	  * @param <E>
	  * @param op1
	  * @param o2
	  * @param ifLess Value to take if {@code op1} is less than {@code op2}
	  * @param ifEqual Value to take if {@code op1} is equal to {@code op2}
	  * @param ifGreater Value to take if {@code op1} is greater than {@code op2}
	  * @param nullIsLess Whether a <code>null</code> reference should be 
	  * treated as less than all other values of type {@code E}.
	  * If this Parameter is <code>null</code>, the reactive boolean will take the value <code>null</code>
	  * @return
	  */
	 public static <E extends Comparable<? super E>> 
	 SealBool comparison(
			 ReadDependency<? extends E> op1, 
			 E o2,
			 Boolean ifLess, Boolean ifEqual, Boolean ifGreater,
			 Boolean nullIsLess
			 ){
		 return op1.mapToBool(o1->{
			 int comp;
			 if(o1==o2) {
				 if(o1==null & nullIsLess==null)
					 return null;			
				 comp=0;
			 }else if(o1==null) {
				 if(nullIsLess==null)
					 return null;
				 comp=nullIsLess?-1:1;
			 }else if(o2==null) {
				 if(nullIsLess==null)
					 return null;
				 comp=nullIsLess?1:-1;
			 }else
				 comp = o1.compareTo(o2);
			 return comp==0?ifEqual:comp<0?ifLess:ifGreater;
		 });
	 }
	 /**
	  * Make a reactive boolean that computes itself to be the result of comparing two
	  * reactive values for equality
	  * @param <E>
	  * @param op1
	  * @param op2
	  * @param ifEqual Value to take if {@code op1} and {@code op2} are equal
	  * @param ifUnequal Value to take if {@code op1} and {@code op2} are not equal
	  * @return
	  */
	 public static <E> 
	 SealBool equalityComparison(
			 ReadDependency<? extends E> op1, 
			 ReadDependency<? extends E> op2,
			 Boolean ifEqual, Boolean ifUnequal
			 ){
		 return Piles.makeBinOp(op1, op2, new SealBool(), (o1, o2)->{
			 if(o1==o2 || Objects.equals(o1, o2)) {
				 return ifEqual;
			 }else {
				 return ifUnequal;
			 }
		 });
	 }
	 /**
	  * Make a reactive boolean that computes itself to be the result of comparing 
	  * a constant to a reactive value for equality
	  * @param <E>
	  * @param o1
	  * @param op2
	  * @param ifEqual Value to take if {@code op1} and {@code op2} are equal
	  * @param ifUnequal Value to take if {@code op1} and {@code op2} are not equal
	  * @return
	  */
	 public static <E> SealBool equalityComparison(E o1, ReadDependency<? extends E> op2, Boolean ifEqual, Boolean ifUnequal){
		 return equalityComparison(op2, o1, ifEqual, ifUnequal);
	 }

	 /**
	  * Make a reactive boolean that computes itself to be the result of comparing 
	  * a reactive value to a constant for equality
	  * @param <E>
	  * @param op1
	  * @param o2
	  * @param ifEqual Value to take if {@code op1} and {@code op2} are equal
	  * @param ifUnequal Value to take if {@code op1} and {@code op2} are not equal
	  * @return
	  */
	 public static <E> 
	 SealBool equalityComparison(
			 ReadDependency<? extends E> op1, 
			 E o2,
			 Boolean ifEqual, Boolean ifUnequal
			 ){
		 return op1.mapToBool(o1->{
			 if(o1==o2 || o1!=null && o1.equals(o2)) {
				 return ifEqual;
			 }else {
				 return ifUnequal;
			 }
		 });
	 }

	 
	 
	 /**
	  * Make a reactive boolean that computes itself to be the result of comparing 
	  * two reactive values for equivalence under the given equivalence relation
	  * @param <E>
	  * @param op1
	  * @param op2
	  * @param ifEqual Value to take if {@code op1} and {@code op2} are equal
	  * @param ifUnequal Value to take if {@code op1} and {@code op2} are not equal
	  * @param eq An equivalence relation
	  * @return
	  */
	 public static <E> 
	 SealBool equalityComparison(
			 ReadDependency<? extends E> op1, 
			 ReadDependency<? extends E> op2,
			 Boolean ifEqual, Boolean ifUnequal,
			 BiPredicate<? super E, ? super E> equivalence
			 ){
		 return Piles.makeBinOp(op1, op2, new SealBool(), (o1, o2)->{
			 return equivalence.test(o1, o2)?ifEqual:ifUnequal;
		 });
	 }
	 /**
	  * Make a reactive boolean that computes itself to be the result of comparing 
	  * a constant to a reactive value for equivalence under the given equivalence relation
	  * @param <E>
	  * @param o1
	  * @param op2
	  * @param ifEqual Value to take if {@code op1} and {@code op2} are equal
	  * @param ifUnequal Value to take if {@code op1} and {@code op2} are not equal
	  * @param eq An equivalence relation
	  * @return
	  */
	 public static <E> SealBool equalityComparison(E o1, ReadDependency<? extends E> op2, Boolean ifEqual, Boolean ifUnequal, BiPredicate<? super E, ? super E> eq){
		 return equalityComparison(op2, o1, ifEqual, ifUnequal, eq);
	 }
	 /**
	  * Make a reactive boolean that computes itself to be the result of comparing 
	  * a reactive value to a constant for equivalence under the given equivalence relation
	  * @param <E>
	  * @param op1
	  * @param o2
	  * @param ifEqual Value to take if {@code op1} and {@code op2} are equal
	  * @param ifUnequal Value to take if {@code op1} and {@code op2} are not equal
	  * @param eq An equivalence relation
	  * @return
	  */
	 public static <E> 
	 SealBool equalityComparison(
			 ReadDependency<? extends E> op1, 
			 E o2,
			 Boolean ifEqual, Boolean ifUnequal,
			 BiPredicate<? super E, ? super E> equivalence
			 ){
		 return op1.mapToBool(o1->{
			 return equivalence.test(o1, o2)?ifEqual:ifUnequal;
		 });
	 }

	 /**
	  * Make a reactive boolean that computes itself to be the result of comparing two
	  * reactive values using the given total ordering relation
	  * @param <E>
	  * @param op1
	  * @param op2
	  * @param ifLess Value to take if {@code op1} is less than {@code op2}
	  * @param ifEqual Value to take if {@code op1} is equal to {@code op2}
	  * @param ifGreater Value to take if {@code op1} is greater than {@code op2}
	  * @param ordering
	  * @return
	  */
	 public static <E> 
	 SealBool comparison(
			 ReadDependency<? extends E> op1, 
			 ReadDependency<? extends E> op2,
			 Boolean ifLess, Boolean ifEqual, Boolean ifGreater,
			 Comparator<? super E> ordering
			 ){
		 return Piles.makeBinOp(op1, op2, new SealBool(), (o1, o2)->{
			 int comp = ordering.compare(o1, o2);
			 return comp==0?ifEqual:comp<0?ifLess:ifGreater;
		 });
	 }
	 /**
	  * Make a reactive boolean that computes itself to be the result of comparing 
	  * a reactive value to a constant using the given total ordering relation
	  * @param <E>
	  * @param op1
	  * @param o2
	  * @param ifLess Value to take if {@code op1} is less than {@code op2}
	  * @param ifEqual Value to take if {@code op1} is equal to {@code op2}
	  * @param ifGreater Value to take if {@code op1} is greater than {@code op2}
	  * @param ordering
	  * @return
	  */
	 public static <E> 
	 SealBool comparison(
			 ReadDependency<? extends E> op1, 
			 E o2,
			 Boolean ifLess, Boolean ifEqual, Boolean ifGreater,
			 Comparator<? super E> ordering
			 ){
		 return op1.mapToBool(o1->{
			 int comp = ordering.compare(o1, o2);
			 return comp==0?ifEqual:comp<0?ifLess:ifGreater;
		 });
	 }
	 /**
	  * Make a reactive boolean that computes itself to be the result of comparing 
	  * a constant to a reactive value using the given total ordering relation

	  * @param <E>
	  * @param o1
	  * @param op2
	  * @param ifLess Value to take if {@code op1} is less than {@code op2}
	  * @param ifEqual Value to take if {@code op1} is equal to {@code op2}
	  * @param ifGreater Value to take if {@code op1} is greater than {@code op2}
	  * @param ordering
	  * @return
	  */
	 public static <E> 
	 SealBool comparison(
			 E o1,
			 ReadDependency<? extends E> op2, 
			 Boolean ifLess, Boolean ifEqual, Boolean ifGreater,
			 Comparator<? super E> ordering
			 ){
		 return comparison(op2, o1, ifGreater, ifEqual, ifLess, ordering);
	 }


	 public static <E> SealBool equal(ReadDependency<? extends E> op1, ReadDependency<? extends E> op2) {
		 return equalityComparison(op1, op2, true, false).setName(op1.dependencyName()+" == "+op2.dependencyName());
	 }
	 public static <E> SealBool unequal(ReadDependency<? extends E> op1, ReadDependency<? extends E> op2) {
		 return equalityComparison(op1, op2, false, true).setName(op1.dependencyName()+" != "+op2.dependencyName());
	 }
	 public static <E> SealBool equal(BiPredicate<? super E, ? super E> equivalence, ReadDependency<? extends E> op1, ReadDependency<? extends E> op2) {
		 return equalityComparison(op1, op2, true, false, equivalence).setName(op1.dependencyName()+" == "+op2.dependencyName());
	 }
	 public static <E> SealBool unequal(BiPredicate<? super E, ? super E> equivalence, ReadDependency<? extends E> op1, ReadDependency<? extends E> op2) {
		 return equalityComparison(op1, op2, false, true, equivalence).setName(op1.dependencyName()+" != "+op2.dependencyName());
	 }
	 public static <E extends Comparable<? super E>> SealBool lessThan(ReadDependency<? extends E> op1, ReadDependency<? extends E> op2) {
		 return lessThan(op1, op2, (Boolean)null);
	 }
	 public static <E extends Comparable<? super E>> SealBool lessThan(ReadDependency<? extends E> op1, ReadDependency<? extends E> op2, Boolean nullIsLess) {
		 return comparison(op1, op2, true, false, false, nullIsLess).setName(op1.dependencyName()+" < "+op2.dependencyName());
	 }
	 public static <E> SealBool lessThan(ReadDependency<? extends E> op1, ReadDependency<? extends E> op2, Comparator<? super E> ordering) {
		 return comparison(op1, op2, true, false, false, ordering).setName(op1.dependencyName()+" < "+op2.dependencyName());
	 }
	 public static <E extends Comparable<? super E>> SealBool lessThanOrEqual(ReadDependency<? extends E> op1, ReadDependency<? extends E> op2) {
		 return lessThanOrEqual(op1, op2, (Boolean)null);
	 }
	 public static <E extends Comparable<? super E>> SealBool lessThanOrEqual(ReadDependency<? extends E> op1, ReadDependency<? extends E> op2, Boolean nullIsLess) {
		 return comparison(op1, op2, true, true, false, nullIsLess).setName(op1.dependencyName()+" <= "+op2.dependencyName());
	 }
	 public static <E> SealBool lessThanOrEqual(ReadDependency<? extends E> op1, ReadDependency<? extends E> op2, Comparator<? super E> ordering) {
		 return comparison(op1, op2, true, true, false, ordering).setName(op1.dependencyName()+" <= "+op2.dependencyName());
	 }
	 public static <E extends Comparable<? super E>> SealBool greaterThanOrEqual(ReadDependency<? extends E> op1, ReadDependency<? extends E> op2) {
		 return greaterThanOrEqual(op1, op2, (Boolean)null);
	 }
	 public static <E extends Comparable<? super E>> SealBool greaterThanOrEqual(ReadDependency<? extends E> op1, ReadDependency<? extends E> op2, Boolean nullIsLess) {
		 return comparison(op1, op2, false, true, true, nullIsLess).setName(op1.dependencyName()+" >= "+op2.dependencyName());
	 }
	 public static <E> SealBool greaterThanOrEqual(ReadDependency<? extends E> op1, ReadDependency<? extends E> op2, Comparator<? super E> ordering) {
		 return comparison(op1, op2, false, true, true, ordering).setName(op1.dependencyName()+" >= "+op2.dependencyName());
	 }
	 public static <E extends Comparable<? super E>> SealBool greaterThan(ReadDependency<? extends E> op1, ReadDependency<? extends E> op2) {
		 return greaterThan(op1, op2, (Boolean)null);
	 }
	 public static <E extends Comparable<? super E>> SealBool greaterThan(ReadDependency<? extends E> op1, ReadDependency<? extends E> op2, Boolean nullIsLess) {
		 return comparison(op1, op2, false, false, true, nullIsLess).setName(op1.dependencyName()+" > "+op2.dependencyName());
	 }
	 public static <E> SealBool greaterThan(ReadDependency<? extends E> op1, ReadDependency<? extends E> op2, Comparator<? super E> ordering) {
		 return comparison(op1, op2, false, false, true, ordering).setName(op1.dependencyName()+" > "+op2.dependencyName());
	 }






	 public static <E> SealBool equal(ReadDependency<? extends E> op1, E op2) {
		 return equalityComparison(op1, op2, true, false).setName(op1.dependencyName()+" == "+op2);
	 }
	 public static <E> SealBool unequal(ReadDependency<? extends E> op1, E op2) {
		 return equalityComparison(op1, op2, false, true).setName(op1.dependencyName()+" != "+op2);
	 }
	 public static <E> SealBool equal(BiPredicate<? super E, ? super E> equivalence, ReadDependency<? extends E> op1, E op2) {
		 return equalityComparison(op1, op2, true, false, equivalence).setName(op1.dependencyName()+" == "+op2);
	 }
	 public static <E> SealBool unequal(BiPredicate<? super E, ? super E> equivalence, ReadDependency<? extends E> op1, E op2) {
		 return equalityComparison(op1, op2, false, true, equivalence).setName(op1.dependencyName()+" != "+op2);
	 }
	 public static <E extends Comparable<? super E>> SealBool lessThan(ReadDependency<? extends E> op1, E op2) {
		 return lessThan(op1, op2, (Boolean)null);
	 }
	 public static <E extends Comparable<? super E>> SealBool lessThan(ReadDependency<? extends E> op1, E op2, Boolean nullIsLess) {
		 return comparison(op1, op2, true, false, false, nullIsLess).setName(op1.dependencyName()+" < "+op2);
	 }
	 public static <E> SealBool lessThan(ReadDependency<? extends E> op1, E op2, Comparator<? super E> ordering) {
		 return comparison(op1, op2, true, false, false, ordering).setName(op1.dependencyName()+" < "+op2);
	 }
	 public static <E extends Comparable<? super E>> SealBool lessThanOrEqual(ReadDependency<? extends E> op1, E op2) {
		 return lessThanOrEqual(op1, op2, (Boolean)null);
	 }
	 public static <E extends Comparable<? super E>> SealBool lessThanOrEqual(ReadDependency<? extends E> op1, E op2, Boolean nullIsLess) {
		 return comparison(op1, op2, true, true, false, nullIsLess).setName(op1.dependencyName()+" <= "+op2);
	 }
	 public static <E> SealBool lessThanOrEqual(ReadDependency<? extends E> op1, E op2, Comparator<? super E> ordering) {
		 return comparison(op1, op2, true, true, false, ordering).setName(op1.dependencyName()+" <= "+op2);
	 }
	 public static <E extends Comparable<? super E>> SealBool greaterThanOrEqual(ReadDependency<? extends E> op1, E op2) {
		 return greaterThanOrEqual(op1, op2, (Boolean)null);
	 }
	 public static <E extends Comparable<? super E>> SealBool greaterThanOrEqual(ReadDependency<? extends E> op1, E op2, Boolean nullIsLess) {
		 return comparison(op1, op2, false, true, true, nullIsLess).setName(op1.dependencyName()+" >= "+op2);
	 }
	 public static <E> SealBool greaterThanOrEqual(ReadDependency<? extends E> op1, E op2, Comparator<? super E> ordering) {
		 return comparison(op1, op2, false, true, true, ordering).setName(op1.dependencyName()+" >= "+op2);
	 }
	 public static <E extends Comparable<? super E>> SealBool greaterThan(ReadDependency<? extends E> op1, E op2) {
		 return greaterThan(op1, op2, (Boolean)null);
	 }
	 public static <E extends Comparable<? super E>> SealBool greaterThan(ReadDependency<? extends E> op1, E op2, Boolean nullIsLess) {
		 return comparison(op1, op2, false, false, true, nullIsLess).setName(op1.dependencyName()+" > "+op2);
	 }
	 public static <E> SealBool greaterThan(ReadDependency<? extends E> op1, E op2, Comparator<? super E> ordering) {
		 return comparison(op1, op2, false, false, true, ordering).setName(op1.dependencyName()+" > "+op2);
	 }






	 public static <E> SealBool equal(E op1, ReadDependency<? extends E> op2) {
		 return equalityComparison(op1, op2, true, false).setName(op1+" == "+op2.dependencyName());
	 }
	 public static <E> SealBool unequal(E op1, ReadDependency<? extends E> op2) {
		 return equalityComparison(op1, op2, false, true).setName(op1+" != "+op2.dependencyName());
	 }
	 public static <E> SealBool equal(BiPredicate<? super E, ? super E> equivalence, E op1, ReadDependency<? extends E> op2) {
		 return equalityComparison(op1, op2, true, false, equivalence).setName(op1+" == "+op2.dependencyName());
	 }
	 public static <E> SealBool unequal(BiPredicate<? super E, ? super E> equivalence, E op1, ReadDependency<? extends E> op2) {
		 return equalityComparison(op1, op2, false, true, equivalence).setName(op1+" != "+op2.dependencyName());
	 }
	 public static <E extends Comparable<? super E>> SealBool lessThan(E op1, ReadDependency<? extends E> op2) {
		 return lessThan(op1, op2, (Boolean)null);
	 }
	 public static <E extends Comparable<? super E>> SealBool lessThan(E op1, ReadDependency<? extends E> op2, Boolean nullIsLess) {
		 return comparison(op1, op2, true, false, false, nullIsLess).setName(op1+" < "+op2.dependencyName());
	 }
	 public static <E> SealBool lessThan(E op1, ReadDependency<? extends E> op2, Comparator<? super E> ordering) {
		 return comparison(op1, op2, true, false, false, ordering).setName(op1+" < "+op2.dependencyName());
	 }
	 public static <E extends Comparable<? super E>> SealBool lessThanOrEqual(E op1, ReadDependency<? extends E> op2) {
		 return lessThanOrEqual(op1, op2, (Boolean)null);
	 }
	 public static <E extends Comparable<? super E>> SealBool lessThanOrEqual(E op1, ReadDependency<? extends E> op2, Boolean nullIsLess) {
		 return comparison(op1, op2, true, true, false, nullIsLess).setName(op1+" <= "+op2.dependencyName());
	 }
	 public static <E> SealBool lessThanOrEqual(E op1, ReadDependency<? extends E> op2, Comparator<? super E> ordering) {
		 return comparison(op1, op2, true, true, false, ordering).setName(op1+" <= "+op2.dependencyName());
	 }
	 public static <E extends Comparable<? super E>> SealBool greaterThanOrEqual(E op1, ReadDependency<? extends E> op2) {
		 return greaterThanOrEqual(op1, op2, (Boolean)null);
	 }
	 public static <E extends Comparable<? super E>> SealBool greaterThanOrEqual(E op1, ReadDependency<? extends E> op2, Boolean nullIsLess) {
		 return comparison(op1, op2, false, true, true, nullIsLess).setName(op1+" >= "+op2.dependencyName());
	 }
	 public static <E> SealBool greaterThanOrEqual(E op1, ReadDependency<? extends E> op2, Comparator<? super E> ordering) {
		 return comparison(op1, op2, false, true, true, ordering).setName(op1+" >= "+op2.dependencyName());
	 }
	 public static <E extends Comparable<? super E>> SealBool greaterThan(E op1, ReadDependency<? extends E> op2) {
		 return greaterThan(op1, op2, (Boolean)null);
	 }
	 public static <E extends Comparable<? super E>> SealBool greaterThan(E op1, ReadDependency<? extends E> op2, Boolean nullIsLess) {
		 return comparison(op1, op2, false, false, true, nullIsLess).setName(op1+" > "+op2.dependencyName());
	 }
	 public static <E> SealBool greaterThan(E op1, ReadDependency<? extends E> op2, Comparator<? super E> ordering) {
		 return comparison(op1, op2, false, false, true, ordering).setName(op1+" > "+op2.dependencyName());
	 }

	 public PileBool setName(String name);
	 @Override public default PileBool setNull() {
		 set(null);
		 return this;
	 }


	 /**
	  * A writable controlled NOT operation.
	  * This constructs a reactive boolean that takes on the <q>exclusive or</q> of the {@code input} and the {@code control}.
	  * Writing to that value will attempt to write to the {@code input}, either directly or invertedly depending on the current value
	  * of the {@code control}.
	  * @param input
	  * @param control
	  * @return
	  */
	 public static SealBool cNot(ReadWriteDependency<Boolean> input, ReadDependency<? extends Boolean> control) {
		 return Piles
				 .sealedNoInitBool()
				 .recomputeS(()->{
					Boolean i = input.get();
					boolean controlValue = Boolean.TRUE.equals(control.get());
					return i==null?null:(i^controlValue);
				 })
				 .seal(i->{
					 boolean controlValue = Boolean.TRUE.equals(control.get());
					 input.set(i==null?null:(i^controlValue));
				 })
				 .parent(input)
				 .name(input.dependencyName()+" <+ "+control.dependencyName())
				 .whenChanged(input, control);
	 }


	 /**
	  * Create a {@link PileBuilder} for reactive booleans
	  * @return
	  */
	 public static PileBuilder<PileBoolImpl, Boolean> rb(){return new PileBuilder<>(new PileBoolImpl()).ordering(Comparator.naturalOrder());}
	 /**
	  * Create a {@link SealPileBuilder} for sealable reactive booleans
	  * @return
	  */
	 public static SealPileBuilder<SealBool, Boolean> sb(){return new SealPileBuilder<>(new SealBool()).ordering(Comparator.naturalOrder());}
	 /**
	  * Create an {@link IndependentBuilder} for independent reactive booleans with a <code>null</code> initial value.
	  * @return
	  */
	 public static IndependentBuilder<IndependentBool, Boolean> ib(){return new IndependentBuilder<>(new IndependentBool(null)).ordering(Comparator.naturalOrder());}
	 /**
	  * Create an {@link IndependentBuilder} for independent reactive booleans
	  * @param init initial value of the {@link Independent}
	  * @return
	  */
	 public static IndependentBuilder<IndependentBool, Boolean> ib(Boolean init){return new IndependentBuilder<>(new IndependentBool(init)).ordering(Comparator.naturalOrder());}
	 /**
	  * Periodically execute the given {@code job} whenever the {@code condition} {@linkplain ReadValueBool#isTrue(Supplier) is true}.
	  * is <code>true</code>. 
	  * @param condition The condition for the job being scheduled. If it becomes <code>null</code> or <code>false</code>, the {@link ScheduledFuture} will be cancelled.
	  * If it is {@link ReadValue#isValid() invalid}, the {@link ScheduledFuture} may or me not get cancelled; if the {@code condition} is merely in a 
	  * {@link Pile#transaction() transaction} but the {@link ScheduledFuture} is still held as its old value, it will not be cancelled. 
	  * @param intervalMillis The scheduling period in milliseconds. There is no initial delay if the {@code condition} changes to <code>true</code>
	  * and the last time it started executing successfully is longer in the past than the interval; otherwise, the initial delay is chosen so that the interval between
	  * the last execution and the first execution of the newly scheduled {@link Future} is approximately {@code intervalMillis}.
	  * @param mayInterrupt Whether it is permissible to interrupt the job when the {@code condition} stops being <code>true</code>
	  * or the returned {@link Pile} closes its any-value-bracket on it for another reason.
	  * @param scheduler The service used to schedule the periodically executing job
	  * @param job The code that should be run periodically.
	  * @return A {@link Pile} containing the currently scheduled future. You should keep a reference to it to prevent the
	  * garbage collector from getting it (thus ending the effect of this method call), and you should ensure it is 
	  * {@link Pile#destroy() destroy}ed manually when you desire the effect to end (otherwise you would have to rely on the 
	  * garbage collector to nondeterministically end it). When the returned {@link Pile} is garbage collected, the {@link ScheduledFuture} will be cancelled.
	  * You can {@link Pile#set(Object) set} the returned {@link Pile} to <code>null</code> to cancel any currently scheduled future 
	  * prematurely even if the {@code condition} remains <code>true</code>
	  */
	 public static Pile<?> whileTrueRepeat(ReadListenDependency<? extends Boolean> condition, long intervalMillis, boolean mayInterrupt, ScheduledExecutorService scheduler, Runnable job){
		 MutRef<ScheduledFuture<?>> last = new MutRef<>();
		 long[] lastRun = {0};
		 Runnable wrapped = ()->{
			 long startRun = System.currentTimeMillis();
			 StandardExecutors.safe(job);	
			 synchronized (last) {
				 lastRun[0] = startRun;
			 }
		 };
		 Consumer<? super Future<?>> kanzler = f->{
			 if(f!=null && !f.isDone()) {
				 if(DEBUG){
					 try {
						 throw new RuntimeException("cancelling");
					 }catch(RuntimeException x) {
						 x.printStackTrace();
					 }
				 }
				 f.cancel(mayInterrupt);
			 }
		 };
		 return Piles
				 .<ScheduledFuture<?>>compute(reco->{
					 if(!ReadValueBool.isTrue(condition)) {
						 if(DEBUG) System.err.println("nulling");

						 reco.fulfill(null);
						 return;
					 }
					 if(DEBUG) {
						 reco.setFailHandler(f->{
							 System.err.println("failHandler: "+f);
						 });
					 }

					 //reco.setFailHandler(kanzler);
					 synchronized (last) {
						 if(last.val!=null) {
							 last.val.cancel(mayInterrupt);
							 try {
								 last.val.get();
							 } catch (InterruptedException e) {
								 if(DEBUG)System.err.println("interrupted");
								 StandardExecutors.interruptSelf();
								 reco.fulfillInvalid();
								 return;
							 } catch (CancellationException e) {
								 if(DEBUG)System.err.println("cancelled");
								 //This exception is expected; it will be thrown when the Future is canceled
							 } catch (ExecutionException e) {
								 log.log(Level.WARNING, "Should not happen", e);
							 } catch (Throwable t) {
								 if(DEBUG)t.printStackTrace();
								 throw t;
							 }
						 }

						 long initialDelay = Math.max(0, intervalMillis - (System.currentTimeMillis() - lastRun[0]));
						 last.val = scheduler.scheduleAtFixedRate(
								 wrapped, 
								 initialDelay, intervalMillis, TimeUnit.MILLISECONDS);
						 if(DEBUG && last.val==null) {
							 System.err.println("Nothing was scheduled!");
						 }
						 reco.fulfill(last.val);
					 }
				 })
				 .delay(0)
				 .anyBracket(ValueBracket.closeOnly(false, kanzler))
				 .runIfWeak(()->{
					 ScheduledFuture<?> lv = last.val;
					 if(lv!=null)
						 last.val.cancel(mayInterrupt);
				 })
				 .whenChanged(condition);
	 }
	 
		/**
		 * Define various monoids for dynamic aggregation over the dependencies.
		 * @author bb
		 *
		 */
		public static interface BoolMonoidOp {
			public static final Boolean OR_NEUTRAL = Boolean.FALSE;
			public static final Boolean AND_NEUTRAL = Boolean.TRUE;
			public static final Boolean XOR_NEUTRAL = Boolean.FALSE;
			public static final BoolMonoidOp AND = (a, b)->(a&&b);
			public static final BoolMonoidOp OR = (a, b)->(a||b);
			public static final BoolMonoidOp XOR = (a, b)->(a^b);
			public static final Consumer<? super PileBuilder<? extends PileImpl<Boolean>,Boolean>> OR_CONFIG = configurator(OR_NEUTRAL, OR);
			public static final Consumer<? super PileBuilder<? extends PileImpl<Boolean>,Boolean>> AND_CONFIG = configurator(AND_NEUTRAL, AND);
			public static final Consumer<? super PileBuilder<? extends PileImpl<Boolean>,Boolean>> XOR_CONFIG = configurator(XOR_NEUTRAL, XOR);

			public boolean apply(boolean a, boolean b);

			/**
			 * Make a {@link IBuilder#configure(Consumer) configurator} 
			 * that sets {@link Pile} so that it computes itself
			 * as the result of aggregating all its {@link Dependency Dependencies}
			 * that are instances of {@link ReadValueBool} using the given monoid. 
			 * @param <V>
			 * @param ifEmpty Value to take if there are no operands; Neutral element of the monoid
			 * @param op The monoid operation
			 * @return
			 */
			public static 
			<V extends PileImpl<Boolean>> 
			Consumer<? super PileBuilder<? extends V,Boolean>> 
			configurator(Boolean ifEmpty, BoolMonoidOp op){
				return vb->{vb.recompute(()->{
					V val = vb.valueBeingBuilt();
					MutRef<Boolean> result = new MutRef<>();
					val.giveDependencies(d->{
						if(d instanceof ReadValueBool) {
							ReadValueBool dd = (ReadValueBool)d;
							Boolean dv = dd.get();
							if(dv==null)
								throw new FulfillInvalid("One of the operands is null");
							Boolean rv = result.val;
							result.val = rv==null?dv:op.apply(rv, dv);
						}
					});
					return result.val==null?ifEmpty:result.val;
				})
					.nameIfUnnamed("Dynamic double aggregator");
				};
			}

		}
	 
		/**
		 * Make a reactive boolean that computes itself 
		 * as the disjunction of its {@link Dependency Dependencies} that are
		 * also {@link ReadValueBool} instances.
		 * @param deps initial {@link Dependency Dependencies}
		 * @return
		 */
		public static PileBoolImpl dynamicOr(Dependency... deps) {
			return buildDynamicDisjunction(new PileBoolImpl()).whenChanged(deps);
		}
		/**
		 * Make a {@link PileBuilder} configured to make the given value compute itself 
		 * as the disjunction of its {@link Dependency Dependencies} that are also
		 * {@link ReadValueDouble} instances. 
		 */
		public static <V extends PileImpl<Boolean>> PileBuilder<V, Boolean> buildDynamicDisjunction(V val){
			return new PileBuilder<>(val).configure(BoolMonoidOp.OR_CONFIG);
		}
		/**
		 * Make a reactive boolean that computes itself 
		 * as the disjunction of its {@link Dependency Dependencies} that are
		 * also {@link ReadValueBool} instances.
		 * @param deps initial {@link Dependency Dependencies}
		 * @return
		 */
		public static PileBoolImpl dynamicAnd(Dependency... deps) {
			return buildDynamicConjunction(new PileBoolImpl()).whenChanged(deps);
		}
		/**
		 * Make a {@link PileBuilder} configured to make the given value compute itself 
		 * as the disjunction of its {@link Dependency Dependencies} that are also
		 * {@link ReadValueDouble} instances. 
		 */
		public static <V extends PileImpl<Boolean>> PileBuilder<V, Boolean> buildDynamicConjunction(V val){
			return new PileBuilder<>(val).configure(BoolMonoidOp.AND_CONFIG);
		}
		/**
		 * Make a reactive boolean that computes itself 
		 * as the disjunction of its {@link Dependency Dependencies} that are
		 * also {@link ReadValueBool} instances.
		 * @param deps initial {@link Dependency Dependencies}
		 * @return
		 */
		public static PileBoolImpl dynamicXor(Dependency... deps) {
			return buildDynamicXor(new PileBoolImpl()).whenChanged(deps);
		}
		/**
		 * Make a {@link PileBuilder} configured to make the given value compute itself 
		 * as the disjunction of its {@link Dependency Dependencies} that are also
		 * {@link ReadValueDouble} instances. 
		 */
		public static <V extends PileImpl<Boolean>> PileBuilder<V, Boolean> buildDynamicXor(V val){
			return new PileBuilder<>(val).configure(BoolMonoidOp.XOR_CONFIG);
		}
	 
}
