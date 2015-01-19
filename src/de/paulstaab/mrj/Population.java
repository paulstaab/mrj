package de.paulstaab.mrj;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import cern.jet.math.Arithmetic;
import cern.jet.random.Binomial;
import cern.jet.random.Poisson;
import cern.jet.random.engine.MersenneTwister;

public class Population {
	
	//-----------------------------------------------
	//Variables
	//-----------------------------------------------
	
	//Used for rounded outputs
	NumberFormat nf = NumberFormat.getInstance(Locale.US);
	
	//The population
	private HashMap<Integer,Integer> typeList = new HashMap<Integer,Integer>();
	
	//Model parameters
	private int size = 0;
	private int time = 0;
	private double mutation;
	private double backmutation;
	private double selection;
	
	//Options
	private boolean alternative = false;
	private boolean human = false;
	private boolean every = false;
	
	//Used for caching values
	private double tempFirstMoment;
	private double tempMeanFitness;
	private int tempFirstMomentCalcTime = -1;
	private int tempMeanFitnessCalcTime = -1;
	
	//Statistics
	int mutationsOfFittestClass = 0;
	int maxMutations = 1;
	
	//Random number generators
	MersenneTwister engine = 
		new MersenneTwister(new java.util.Date());
	Poisson mutationGenerator;
	
	
	
	//-----------------------------------------------
	//Constructor
	//-----------------------------------------------
	public Population(	int size, double mutation, double selection, 
						double backmutation,  int initialMutations, 
						boolean alternative, boolean human, boolean every ){
		
		this.size = size;
		this.typeList.put(initialMutations, size);
		
		if ( this.checkMutation(mutation) ) {
			this.mutation = mutation;
			this.mutationGenerator = new Poisson(this.mutation, engine);
		}
		else throw new 
			IllegalArgumentException("Mutation parameter must be greater than 0");
		
		if ( this.checkBackMutation(backmutation) ) 
			this.backmutation = backmutation;
		else throw new 
			IllegalArgumentException("BackMutation parameter must be in [0,1]");		
		
		if ( this.checkSelection(selection) ) 
			this.selection = selection;
		else throw new 
			IllegalArgumentException("Selection parameter must be between 0 and 1");	
		
		if (alternative) this.alternative = true;
		if (human) this.human = true;
		if (every) this.every = true;
	}
	
	
	
	//-----------------------------------------------
	//Parameter checks
	//-----------------------------------------------
	private boolean checkMutation(double mutation){
		if ( mutation >= 0 ) return true;
		else return false;
	}
	
	private boolean checkBackMutation(double backMutation){
		if ( backMutation >= 0 && backMutation <= 1 ) return true;
		else return false;
	}
	
	private boolean checkSelection(double selection){
		if ( selection >= 0 && selection <= 1 ) return true;
		else return false;
	}	
	
	
	
	//-----------------------------------------------
	//Calcuations
	//-----------------------------------------------
	private double x(int k){
		double xk = 0;
		if (typeList.containsKey(k)) xk = (double)typeList.get(k) / size;
		return xk;
	}
	
	private int multinominialSample(Map<Integer, Double> probWeights) 
		throws Exception {
		double random = this.engine.nextDouble();
		double quantil = 0;
		int last = 0;
		for (Entry<Integer, Double> entry : probWeights.entrySet() ) {
			quantil += entry.getValue();
		    if (random < quantil) {
		    	return( entry.getKey() );
		    }
		    last = entry.getKey();
		}
		if (quantil < 0.9999 || Double.isNaN(quantil)) 
			throw new IllegalArgumentException("Error doing multinomial sampling");
		return ( last ); 	//Error taken in account for speedoptimisation. 
							//Should only happen in every 10000 case...
	}

	private double calcSelection(int i){
		if (x(i) == 0) return 0.0;
		else return x(i) * Math.pow(1-selection, i-mutationsOfFittestClass);
	}

	private double meanFitness() throws Exception{
		double meanFitness = 0;
		if (this.tempMeanFitnessCalcTime == this.time) 
			meanFitness = this.tempMeanFitness;
		else{
			for (int k : this.typeList.keySet()){
				meanFitness += calcSelection(k) ;
			}
			this.tempMeanFitnessCalcTime = this.time;
			this.tempMeanFitness = meanFitness;
		}
		if (meanFitness == 0) throw new Exception("meanFitness = 0!");
		return meanFitness;
	}
	
	private double firstMoment()
	{
		double firstMoment = 0;
		if (this.tempFirstMomentCalcTime == this.time) 
			firstMoment = this.tempFirstMoment;
		else {
			for (int k : this.typeList.keySet()){
				firstMoment += k * x(k);
			}
			this.tempFirstMomentCalcTime = time;
			this.tempFirstMoment = firstMoment;
		}
		return firstMoment;
	}
	
	
	
	//-----------------------------------------------
	//Reproduction functions
	//-----------------------------------------------
	public void reproduce() throws Exception{
		if (alternative) this.reproduce_alternative();
		else this.reproduce_normal();
	}
	
	private void reproduce_normal() throws Exception{
		//Compute reproducing probabilites according to selection 
		HashMap<Integer,Double> probWeights = new HashMap<Integer,Double>();
		for (int i : this.typeList.keySet()){
			probWeights.put( i , calcSelection(i)/meanFitness() );
		}
		
		//Draw N siblings 
		HashMap<Integer, Integer> typeList= new HashMap<Integer, Integer>();
		int minMutations = Integer.MAX_VALUE;
		for (int i=1; i<=this.size; i++){
			//Draw a sibling,
			int offspring = this.multinominialSample(probWeights);
			//remove mutations
			if (this.backmutation > 0 && offspring > 0) 
				offspring -= 
					new Binomial(offspring, this.backmutation, this.engine).nextInt();
			//add mutations
			offspring += mutationGenerator.nextInt();

			if (offspring < minMutations) minMutations = offspring;
			//and create a new typeList
			if ( typeList.containsKey(offspring) ){
				int tmp = typeList.get(offspring);
				typeList.remove(offspring);
				typeList.put(offspring, tmp+1);
			}
			else typeList.put(offspring, 1);
		}
		//Save the new population
		this.mutationsOfFittestClass = minMutations;
		this.typeList = typeList;
		time++;
	}
	
	private void reproduce_alternative() throws Exception{
		HashMap<Integer,Double> probWeights = new HashMap<Integer,Double>();
		double sum = 0;
		
		//calculate expected frequencys for next generation
		int k=-1; while(sum < 0.9999) {
			k++;
			double xk = 0;
			for (int i=Math.max(0,this.mutationsOfFittestClass-15) ; i<=k; i++){
				double xki = 0;
				for (int j=i; j<=this.maxMutations; j++) {
					if (j==0 || (backmutation == 0 && i == j )) xki += calcSelection(j); 
					//Can't be calculated by Binomial.class
					else if (backmutation != 0) {
							xki += calcSelection(j)
				    	     * new Binomial(j,backmutation,engine).pdf(j-i);
					}
				}
				//xki = xki * new Poisson(mutation,engine).pdf(k-i);
				xki = xki * Math.pow(mutation, k-i) / Arithmetic.factorial(k-i);
				xk += xki;
				//System.out.println("--- k:"+k+" i:"+i+" xki:"+xki+" xk:"+xk);
			}
			xk = xk * Math.pow(Math.E, -mutation) / meanFitness(); 
			probWeights.put(k, xk);
			sum += xk;
			//System.out.println("k:" + k + " Xk:" + xk + " sum:" + sum);
		}
		
		//sample new individuals
		HashMap<Integer, Integer> typeList = new HashMap<Integer, Integer>();
		int minMutations = Integer.MAX_VALUE;
		int maxMutations = 0;
		for (int i=1; i<=this.size; i++){
				//Draw a sibling,
				int offspring = this.multinominialSample(probWeights);
				
				if (offspring < minMutations) minMutations = offspring;
				if (offspring > maxMutations) maxMutations = offspring;
				
				//and create a new typeList
				if ( typeList.containsKey(offspring) ){
					int tmp = typeList.get(offspring);
					typeList.remove(offspring);
					typeList.put(offspring, tmp+1);
				}
				else typeList.put(offspring, 1);
		}
		//Save the new population
		time++;
		this.mutationsOfFittestClass = minMutations;
		this.maxMutations = maxMutations;
		this.typeList = typeList;
	}	

	
	
	
	//-----------------------------------------------
	//Output
	//-----------------------------------------------
	public String toString() {
		if (human) {
			String value = "";
			
			if (!every) value += "t:" + this.time / this.size + "N "; 
			else value += "t:" + this.time + " ";
			
			value += "N:" + this.size + " ";
			value += "s:" + this.selection + " ";
			value += "m:" + this.mutation + " ";
			value += "b:" + this.backmutation + " ";
			
			value += "K*:" + Integer.toString(this.mutationsOfFittestClass) + " ";
			value += "M1:" + nf.format(firstMoment() - mutationsOfFittestClass) + " ";
			
			ArrayList<Integer> sortedKeys = 
				new ArrayList<Integer>(this.typeList.keySet());
			Collections.sort(sortedKeys);
			int lastnumber = sortedKeys.get(sortedKeys.size()-1);
			
			for (int key=0; key <= lastnumber; key++){
				if (sortedKeys.contains(key))
					value += 
						Integer.toString(key)+":" + 
						Integer.toString(this.typeList.get(key)) + " ";
				else 
					value += 
						Integer.toString(key)+":0 ";
			}
			return value;
		}
		
		else {
			String value = "";
			
			if (!every) value += this.time / this.size + " "; 
			else value += this.time + " ";
			
			value += this.size + " ";
			value += this.selection + " ";
			value += this.mutation + " ";
			value += this.backmutation + " ";
			value += Integer.toString(this.mutationsOfFittestClass) + " ";
			value += nf.format(firstMoment() - mutationsOfFittestClass) + " ";

			
			ArrayList<Integer> sortedKeys = new ArrayList<Integer>(typeList.keySet());
			Collections.sort(sortedKeys);
			int lastnumber = sortedKeys.get(sortedKeys.size()-1);
			
			for (int key=this.mutationsOfFittestClass; key <= lastnumber; key++){
				if (sortedKeys.contains(key))
					value += Integer.toString(this.typeList.get(key)) + ";";
				else 
					value += "0;";
			}
			return value;
		}
	}
		
	public String printHeading() {
		return "t N s lambda mu k m1 distribution";
	}
}
