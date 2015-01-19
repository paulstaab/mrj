package de.paulstaab.mrj;

public class Main {
	
	public static void help(){
		System.out.println("MRJ - Muller's ratchet in Java");
		System.out.println("Simulates a population of asexual " +
				           "individuals evolving according to Muller's " +
				           "ratchet with compensatory mutations.");
		System.out.println("");
		System.out.println("Usage: " +
				"mrj [OPTION] N=1000 t=1000 m=0.4 s=0.1 b=0.0001");
		System.out.println("");
		System.out.println("where");
		System.out.println("N\t is population size");
		System.out.println("t\t is the number of generations to simulate. " +
				"As default t is interpreted as t*N generations (see -e)");
		System.out.println("m\t is the mutation parameter");
		System.out.println("s\t is the selection parameter");
		System.out.println("b\t is the compensatory mutation parameter");
		System.out.println("");
		System.out.println("OPTIONs");
		System.out.println("-a  | --alternative \t " +
				"Uses alternative reproduction function. Much slower.");
		System.out.println("-d  | --dont-rescale \t " +
				"Don't rescale time and parameters by 1/N");
		System.out.println("-h  | --help \t\t " +
				"displays this message.");
		System.out.println("-u  | --human \t\t " +
				"generates an output that is better readable by humans ");
		System.exit(0);
	}
	
	
	public static void main(String[] args) {
		try{
			//Get parameters
			int t = 1000;
			int N = 1000;
			double m = 0.4;
			double s = 0.1;
			double b = 0.0001;
			int initialMutations = 0;
			
			boolean header = false;
			boolean human = false;
			boolean alternative = true;
			boolean every = false;
			
			for (String arg : args){
				if (arg.startsWith("t")) t=Integer.valueOf(arg.substring(2));
				else if (arg.startsWith("N")) N=Integer.valueOf(arg.substring(2));
				else if (arg.startsWith("m")) m=Double.valueOf(arg.substring(2));
				else if (arg.startsWith("s")) s=Double.valueOf(arg.substring(2));
				else if (arg.startsWith("b")) b=Double.valueOf(arg.substring(2));
				else if (arg.startsWith("i")) 
					initialMutations=Integer.valueOf(arg.substring(2));
				else if ( arg.contentEquals(("--header")) ) header = true;
				else if ( arg.contentEquals(("--human")) ) human = true;
				else if ( arg.contentEquals(("--alternative")) ) alternative = true;
				else if ( arg.contentEquals(("--dont-rescale")) ) every = true;
				else if ( arg.contentEquals(("--help")) ) help();
				else if (arg.startsWith("-")) {
					if ( arg.contains("a") ) alternative = true;
					if ( arg.contains("d") ) every = true;
					if ( arg.contains("h") ) help();
					if ( arg.contains("u") ) human = true;
					}
				else {
					throw new IllegalArgumentException("Unknown argument given.");
				}
			}
			
			//Initialisation
			Population pop = new Population(N, m, s, b, initialMutations, 
								alternative, human, every);
			
			if (header) System.out.println( pop.printHeading() );
			else{
				System.out.println(pop.toString());
				if (!every) t=t*N;
				int outputEvery = N;
				if (every) outputEvery = 1; 
				
				//Simulation
				for (int i=1; i<=t; i++){
					pop.reproduce();
					if (i % outputEvery == 0) 
						System.out.println( pop.toString() );
				}	
			}			
		}
		catch (Exception e){
			e.printStackTrace();
		}
	}
}