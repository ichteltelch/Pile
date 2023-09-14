package pile.specialized_double;

import java.util.function.Supplier;

import pile.specialized_double.combinations.ReadWriteListenDependencyDouble;


/**
 * A bundle of a {@link ReadWriteListenDependencyDouble reactive double value} with
 * a text and an increment value, which may be useful for displaying it in a GUI.
 * @author bb
 */
public interface FieldDouble {
	public ReadWriteListenDependencyDouble value();
	public String getText();
	public double getIncrement();
	public static FieldDouble make(Supplier<? extends String> text, double increment, ReadWriteListenDependencyDouble value) {
		return new FieldDouble() {
			@Override public ReadWriteListenDependencyDouble value() {return value;}
			@Override public String getText() {return text.get();}
			@Override public double getIncrement() {return increment;}
		};
	}
}
