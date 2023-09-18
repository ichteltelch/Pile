package pile.examples;

import java.util.concurrent.TimeUnit;

import pile.aspect.combinations.Pile;
import pile.aspect.listen.ValueListener;
import pile.impl.Piles;
import pile.interop.exec.StandardExecutors;
import pile.specialized_bool.PileBool;
import pile.specialized_int.IndependentInt;

/**
 * A demonstration of the basic functionality of Pile.
 * @author bb
 *
 */
public class Basic {
	public static void main(String[] args) throws InterruptedException {
		// Let's build a dependency graph. 
		//First, an input:
		IndependentInt a = Piles // Getting a builder for an Independent because it  
				//                  doesn't need to have dependencies or be invalid or
				.independent(2) //Initial value is 2.  
				.neverNull() //No null values allowed
				.bounds(0, 4) //Limits the range of values
				.name("a") //Give it a name so we can better trace what's going on
				.build(); // Configure the Independent and get it
		// Let's make another input to the dependency graph.
		Pile<Integer> b = Piles // Using PileImpl version because we want to make it invalid, 
				//                 and there's no "invalidatable Independent" currently
				.init(3) //Initial value is 3  
				.name("b") //Give it a name so we can better trace what's going on
				.build();// Configure the Pile (actually a PileImpl) and get it
		System.out.println(a);
		System.out.println(b);

		// Now, let's compute the sum of the two inputs
		Pile<Integer> sum = Piles 
				.compute(()->a.get()+b.get()) //Could have used the sum() convenience method,
				//                             but wanted to show how to do it yourself
				.name("a+b") //Give it a name so we can better trace what's going on
				.whenChanged(a, b); //Declare the dependencies, configure and get the Pile
		Pile<Integer> prod = a.times(b)//Here we use the times() method, for convenience
				.setName("a·b") //Give it a name so we can better trace what's going on
				; 

		//We'll need this for the next part, in order to 
		//decide dynamically which dependencies to use
		//Note that we can't create this reactive value anew each 
		//time summary is recomputed because that would be considered 
		//a different dependency, leading to a never-ending loop of 
		//dependency recording due to "changed dependencies".
		PileBool aIsBigger = a.greaterThan(b, true);

		//This time we'll use dynamic dependencies to compute some summary text
		//(It's a bit contrived because, although there are conditional dependencies
		// here on a and b, the Pile will depend on both of them transitively anyways.)
		//Also, let's bring the Recomputation object into scope to show how that is done
		Pile<String> summary = Piles
				.<String>computeStaged(reco->{ //Reco represents the Recomputation
					//Access all required values to use them later as well as 
					// record them as dependencies
					//Actually, the next three are static dependencies 
					//and we could declare them as such, but for clarity's 
					//sake lets not mix features too much
					boolean aIsBigger_value = aIsBigger.isTrue();
					Integer sum_value = sum.get(); 
					Integer prod_value = prod.get(); 
					//Whether we access a or b is conditional on aIsBigger's value
					Integer biggerInput = aIsBigger_value? a.get() : b.get();

					//Now that all dependencies have been accessed, 
					//let's avoid unnecessary computations in
					//case the Recomputation is in dependency scouting mode
					if(reco.terminateDependencyScout())
						return null;

					//Compute the result using the previously loaded values
					//This stage of the computation will be run with a delay of 200ms,
					//Hence it is given by a Runnable which is returned to
					//the Recomputer that the builder is setting up for us.
					return ()->{
						StringBuilder result = new StringBuilder();
						result.append("Sum is: ").append(sum_value);
						result.append("; Product is: ").append(prod_value);
						result.append("; Maximum is: ").append(biggerInput);
						//Fulfill the Recomputation with the result,
						//determining the new value of the Pile
						reco.fulfill(result.toString());
					};
				})
				.delay(200) //Delay the computation 
				.name("summary") //Give it a name so we can better trace what's going on
				.dd(); //Tell the builder to use dynamic dependencies, build the Pile and get it 

		System.out.println(sum); 
		System.out.println(prod);
		System.out.println(summary);



		long startTime = System.currentTimeMillis();
		//Add a ValueListener to the inputs and intermediate value that
		//lets us observe the changes as they happen
		ValueListener cl = e->{
			long now = System.currentTimeMillis();
			long atTime = now - startTime;
			System.out.println("Changed at "+atTime+" ms: "+e.getSource());
			System.out.println();
		};
		a.addValueListener(cl);
		b.addValueListener(cl);
		sum.addValueListener(cl);
		prod.addValueListener(cl);
		summary.addValueListener(cl);





		//Schedule some changes to the inputs
		StandardExecutors.delayed().schedule(()->a.set(20), 300, TimeUnit.MILLISECONDS);
		StandardExecutors.delayed().schedule(()->b.permaInvalidate(), 700, TimeUnit.MILLISECONDS);
		StandardExecutors.delayed().schedule(()->b.set(5), 1100, TimeUnit.MILLISECONDS);
		StandardExecutors.delayed().schedule(()->a.set(1), 1500, TimeUnit.MILLISECONDS);

		//Periodically poll the outputs
		int stepsizeMs = 100;
		for(int i=0; i<20; ++i) {
			System.out.println();
			System.out.println("Time step: "+i*stepsizeMs+" ms");
			System.out.println(sum);
			System.out.println(prod);
			System.out.println(summary);
			Thread.sleep(stepsizeMs);
		}
		/*
		 * Here's what happens (times are approximate):
		 * Initially, 
		 * sum is 5
		 * prod is 6
		 * summary is invalid
		 * 
		 * After 200 ms, 
		 * summary finishes recomputing itself and becomes 
		 * "Sum is: 5; Product is: 6; Maximum is: 3"
		 * 
		 * After 300 ms,
		 * a changes to 4 (It was set to 20, but the upper bound was 4 so that got corrected)
		 * prod recomputes itself immediately to be 12
		 * sum recomputes itself immediately to be 7
		 * summary becomes invalid
		 * 
		 * After 500 ms,
		 * summary finishes recomputing itself and becomes
		 * "Sum is: 7; Product is: 12; Maximum is: 4"
		 * 
		 * After 700 ms,
		 * b becomes invalid 
		 * and so does everything that depends on it
		 * 
		 * After 1100 ms,
		 * b is set to 5
		 * sum recomputes itself immediately to be 9
		 * prod recomputes itself immediately to be 20
		 * summary remains invalid but starts recomputing itself
		 * 
		 * After 1300 ms,
		 * summary finishes recomputing itself and becomes 
		 * "Sum is: 9; Product is: 20; Maximum is: 5"
		 * 
		 * After 1500 ms,
		 * a is set to 1
		 * sum recomputes itself immediately to be 6
		 * prod recomputes itself immediately to be 5
		 * summary becomes invalid
		 * 
		 * After 1700 ms,
		 * summary finishes recomputing itself and becomes 
		 * "Sum is: 6; Product is: 5; Maximum is: 5"
		 */

	}
}
