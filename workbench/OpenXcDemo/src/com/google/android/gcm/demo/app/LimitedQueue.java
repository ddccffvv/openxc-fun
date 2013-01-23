package com.google.android.gcm.demo.app;

import java.util.LinkedList;

import com.openxc.measurements.BaseMeasurement;
import com.openxc.units.Degree;

public class LimitedQueue<E extends BaseMeasurement<Degree>> {
	private LinkedList<E> internal = new LinkedList<E>();
	
	public LimitedQueue(){
		internal.add(null);
		internal.add(null);
	}
	
	public void add(E element){
		Double d = element.getValue().doubleValue();
		if(internal.getLast()==null)
			internal.add(element);
		Double e = internal.getLast().getValue().doubleValue();
		if(!e.equals(d)){
			internal.removeFirst();
			internal.add(element);
		}
	}
	
	public Double[] getElements(){
		Double[] temp = new Double[2];
		temp[0] = internal.get(0).getValue().doubleValue();
		temp[1] = internal.get(1).getValue().doubleValue();
		return temp;
	}
}
