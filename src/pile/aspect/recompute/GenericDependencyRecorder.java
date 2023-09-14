package pile.aspect.recompute;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import pile.aspect.Dependency;

/**
 * A dependency recorder that records all dependencies in a {@link Collection}
 * and optionally dispatches the recorded dependencies to another {@link DependencyRecorder}.
 * @author bb
 *
 */
public class GenericDependencyRecorder implements DependencyRecorder{
	ArrayList<Dependency> record;
	DependencyRecorder outer;
	/**
	 * 
	 * @param outer {@link #recordDependency(Dependency)} is forwarded to this recorder
	 * after the {@link Dependency} has been recorded by {@code this}
	 */
	public GenericDependencyRecorder(DependencyRecorder outer){
		this.outer=outer;
	}
	@Override
	public Recomputation<?> getRecomputation() {
		return outer==null?null:outer.getRecomputation();
	}

	@Override
	public void recordDependency(Dependency d) {
		if(record==null)
			record=new ArrayList<>();
		record.add(d);
		if(outer!=null)
			outer.recordDependency(d);
	}
	/**
	 * @return the recorded dependencies, without ordering or duplications
	 */
	public Set<Dependency> getRecorded(){
		if(record==null)
			return Collections.emptySet();
		switch(record.size()) {
		case 0:				return Collections.emptySet();
		case 1:				return Collections.singleton(record.get(0));
		default: 			return new HashSet<>(record);
		}
	}
}