package pile.specialized_String;

import java.util.Comparator;
import java.util.function.Predicate;

import pile.aspect.Depender;
import pile.aspect.WriteValue;
import pile.aspect.combinations.ReadDependency;
import pile.aspect.combinations.ReadListenDependency;
import pile.aspect.combinations.ReadWriteListenDependency;
import pile.builder.IndependentBuilder;
import pile.builder.PileBuilder;
import pile.builder.SealPileBuilder;
import pile.impl.Independent;
import pile.impl.Piles;
import pile.impl.Piles.AggregationMonoid;
import pile.impl.Piles.SidemostFulfilling;
import pile.specialized_Comparable.PileComparable;
import pile.specialized_String.combinations.ReadListenDependencyString;
import pile.specialized_String.combinations.ReadWriteListenDependencyString;
import pile.utils.Functional;

public interface PileString 
extends Depender, ReadWriteListenDependencyString, PileComparable<String>{
	public static final ConstantString NULL = Piles.constant((String)null);
	public static final ConstantString EMPTY = Piles.EMPTY_STRING;
	/**
	 * A constant string with the value {@code "null"}
	 */
	public static final ReadListenDependencyString CONST_QUOTED_NULL = Piles.constant("null");

	@Override public default PileString setNull() {
		set(null);
		return this;
	}
	/** Specialization of {@link Piles#readOnlyWrapperIdempotent(ReadDependency)} */
	public static ReadListenDependencyString readOnlyWrapperIdempotent(ReadDependency<? extends String> in){
		if(in instanceof SealString) {
			SealString cast = (SealString)in;
			if(cast.isDefaultSealed())
				return cast;
		}else if(in instanceof ConstantString) {
			ConstantString cast = (ConstantString) in;
			return cast;	
		}
		return Piles.makeReadOnlyWrapper(in, new SealString());
	}
	/** Specialization of {@link Piles#readOnlyWrapper(ReadDependency)} */
	public static SealString readOnlyWrapper(ReadDependency<? extends String> in){
		return Piles.makeReadOnlyWrapper(in, new SealString());
	}
	/**
	 * The Aggregator for concatenating strings.
	 */
	public static AggregationMonoid<Object, ReadListenDependencyString> concatAggregation=
			new AggregationMonoid<Object, ReadListenDependencyString>() {

		@Override
		public ReadListenDependencyString constantNeutral() {
			return EMPTY;
		}

		@Override
		public ReadListenDependencyString apply(ReadListenDependency<? extends Object> op1,
				ReadListenDependency<? extends Object> op2) {
			//			ReadListenDependencyString mop1 = op1 instanceof ReadListenDependencyString?(ReadListenDependencyString)op1:op1.mapToString(String::valueOf);
			//			ReadListenDependencyString mop2 = op2 instanceof ReadListenDependencyString?(ReadListenDependencyString)op2:op2.mapToString(String::valueOf);
			return Piles.concatStrings(op1, op2);
		}

		@Override
		public ReadListenDependencyString inject(ReadListenDependency<? extends Object> o) {
			if(o instanceof ReadListenDependencyString)
				return readOnlyWrapperIdempotent((ReadListenDependencyString)o);
			return readOnlyWrapperIdempotent(o.mapToString(String::valueOf));
		}

	};
	/**
	 * Specialization of {@link Piles.LeftmostFulfilling}
	 * @author bb
	 *
	 */
	public static class LeftmostFulfilling extends SidemostFulfilling<String, SealString>{
		public static LeftmostFulfilling NOT_NULL = new LeftmostFulfilling(Functional.IS_NOT_NULL, null);
		public LeftmostFulfilling(Predicate<? super String> mustFulfill, String ifNone) {
			super(mustFulfill, ifNone);
		}

		@Override
		public SealString apply(ReadListenDependency<? extends String> op1,	ReadListenDependency<? extends String> op2) {
			return applyPreferring(op1, op2);
		}
		@Override
		protected SealPileBuilder<SealString, String> makeBuilder() {
			return sb();
		}
		protected SealString makeConstant(String e){
			return Piles.sealedConstant(e);
		}
	}
	/**
	 * Specialization of {@link Piles.RightmostFulfilling}
	 * @author bb
	 *
	 */
	public static class RightmostFulfilling extends SidemostFulfilling<String, SealString>{
		public static LeftmostFulfilling NOT_NULL = new LeftmostFulfilling(Functional.IS_NOT_NULL, null);
		public RightmostFulfilling(Predicate<? super String> mustFulfill, String ifNone) {
			super(mustFulfill, ifNone);
		}

		@Override
		public SealString apply(ReadListenDependency<? extends String> op1,	ReadListenDependency<? extends String> op2) {
			return applyPreferring(op2, op1);
		}
		@Override
		protected SealPileBuilder<SealString, String> makeBuilder() {
			return sb();
		}
		protected SealString makeConstant(String e){
			return Piles.sealedConstant(e);
		}
	}
	
	



	/**
	 * Create a {@link PileBuilder} for reactive strings
	 * @return
	 */
	public static PileBuilder<PileStringImpl, String> rb(){return new PileBuilder<>(new PileStringImpl()).ordering(Comparator.naturalOrder());}
	/**
	 * Create a {@link SealPileBuilder} for sealable reactive strings
	 * @return
	 */
	public static SealPileBuilder<SealString, String> sb(){return new SealPileBuilder<>(new SealString()).ordering(Comparator.naturalOrder());}
	/**
	 * Create an {@link IndependentBuilder} for independent reactive strings with a <code>null</code> initial value.
	 * @return
	 */
	public static IndependentBuilder<IndependentString, String> ib(){return new IndependentBuilder<>(new IndependentString(null)).ordering(Comparator.naturalOrder());}
	/**
	 * Create an {@link IndependentBuilder} for independent reactive strings
	 * @param init initial value of the {@link Independent}
	 * @return
	 */	
	public static IndependentBuilder<IndependentString, String> ib(String init){return new IndependentBuilder<>(new IndependentString(init)).ordering(Comparator.naturalOrder());}

	/**
	 * Makes a reactive string that can store <code>null</code> values even though
	 * the {@code backing} reactive string cannot. 
	 * This is achieved by storing <code>null</code> as the empty string and
	 * adding a space character to the front of 
	 * empty strings or strings starting with a space character 
	 * @param back
	 * @return
	 */
	public static SealString nullableWrapper(ReadWriteListenDependency<String> back) {
		SealPileBuilder<SealString, String> sb = sb();
		SealString vbb = sb.valueBeingBuilt();
		WriteValue<String> setter = vbb.makeSetter();
		return sb
				.recomputeS(()->{
					String s = back.get();
					if(s.length()==0)
						return null;
					if(s.charAt(0)==' ')
						return s.substring(1);
					return s;
				})
				.seal(v->{
					try {
						vbb.__beginTransaction();
						if(v==null)
							back.set("");
						else if(v.length() == 0 || v.charAt(0)==' ')
							back.set(" "+v);
						else
							back.set(v);
						setter.set(v);
					}finally {
						vbb.__endTransaction();
					}
				})
				.whenChanged(back);
	}
}
