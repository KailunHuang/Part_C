package mycontroller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Stack;

import controller.CarController;
import tiles.GrassTrap;
import tiles.HealthTrap;
import tiles.LavaTrap;
import tiles.MapTile;
import tiles.TrapTile;
import tiles.MapTile.Type;
import utilities.Coordinate;
import world.Car;
import world.WorldSpatial;
import world.WorldSpatial.Direction;
import world.WorldSpatial.RelativeDirection;

public class MyAIController extends CarController{
	private final double LavaHP=-5;
	private final double HealthHP=1.25;
	private final double MaxHP=100;
	
	private HashMap<Coordinate,MapTile> map;
	private HashMap<Coordinate,Boolean> detected;
	private HashMap<Coordinate,Boolean> stepped;
	private Stack<Stack<Coordinate>> traceBack;
	private Stack<Coordinate> currentTrace; // A stack of Coordinates which leads from 1 point to another, there can't be 2 same Coordinates
	private HashMap<Coordinate,Direction> nextToLava;// All the entry points of lava and the direction of entering
	private HashMap<Coordinate,Double> HPlosses;// Required HP to go through from all the entry points
	private HashMap<Coordinate,Stack<Coordinate>> routeThroughLava;// The routes each entry would take to the next area
	private ArrayList<Coordinate> safeLava;//the route that takes the least HP to pass through lava
	private enum States {Beginning,Exploring,ExploringGrass,BackTracking,NextArea,ThroughLava,FindKey,GetKey,GetOut};
	private States state;

	public MyAIController(Car car) {
		super(car);
		map=getMap();
		detected=new HashMap<>();
		stepped=new HashMap<>();
		for (Coordinate c:map.keySet()) {
			if (map.get(c).isType(Type.TRAP)){
				detected.put(c, Boolean.FALSE);
			}else {
				detected.put(c, Boolean.TRUE);
			}
			stepped.put(c, Boolean.FALSE);
		}
		state=States.Beginning;
		traceBack=new Stack<>();
		currentTrace=null;
		nextToLava=new HashMap<>();
		HPlosses=new HashMap<>();
		routeThroughLava=new HashMap<>();
		safeLava=new ArrayList<>();
	}

	@Override
	public void update() {
		HashMap<Coordinate, MapTile> currentView = getView();
		Coordinate currentC=new Coordinate(getPosition());
		Direction orientation=getOrientation();
		stepped.replace(currentC, Boolean.TRUE);
		for(Coordinate c:currentView.keySet()) {
			if(currentView.get(c).isType(Type.TRAP)) {
				map.replace(c,currentView.get(c));
				if (((TrapTile) map.get(c)).getTrap().equals("mud")){
					map.replace(c, new MapTile(Type.WALL));
				}
				detected.replace(c, Boolean.TRUE);
			}
		}
		MapTile ahead=map.get(ahead(orientation,currentC));
		MapTile left=map.get(left(orientation,currentC));
		MapTile right=map.get(right(orientation,currentC));
		switch (state){
		case Beginning:
			if(checkAhead(orientation,currentC)) {
				applyReverseAcceleration();
				stepped.replace(currentC, Boolean.FALSE);
			}else if(getSpeed()>0) {//can only stay still or move backwards in Beginning state
				applyBrake();
				traceBack.push(new Stack<>());
				traceBack.peek().push(currentC);
				if(map.get(currentC) instanceof GrassTrap) {
					state=States.ExploringGrass;
				}else {
					state=States.Exploring;
				}
			}
			break;
		case Exploring:
			if(getSpeed()==0) {
				applyForwardAcceleration();
				break;
			}
			traceBack.peek().push(currentC);
			if(((ahead.isType(Type.TRAP) && ((TrapTile)ahead).getTrap().equals("lava")) ||
					(left.isType(Type.TRAP) && ((TrapTile)left).getTrap().equals("lava")) ||
					(right.isType(Type.TRAP) && ((TrapTile)right).getTrap().equals("lava"))) &&
					!nextToLava.containsKey(currentC)) {
				HPlosses.put(currentC, null);
				routeThroughLava.put(currentC, null);
				if(ahead.isType(Type.TRAP) && ((TrapTile)ahead).getTrap().equals("lava")) {
					nextToLava.put(currentC, orientation);
				}else if(left.isType(Type.TRAP) && ((TrapTile)left).getTrap().equals("lava")) {
					nextToLava.put(currentC, WorldSpatial.changeDirection(orientation, RelativeDirection.LEFT));
				}else if(right.isType(Type.TRAP) && ((TrapTile)right).getTrap().equals("lava")) {
					nextToLava.put(currentC, WorldSpatial.changeDirection(orientation, RelativeDirection.RIGHT));
				}
			}
			if(ahead.isType(Type.TRAP) &&
					((TrapTile)ahead).getTrap().equals("grass") &&
					!stepped.get(ahead(orientation,currentC))) {
				forkNewTrace(orientation,currentC);
				state=States.ExploringGrass;
				break;
			}else if(left.isType(Type.TRAP) &&
					((TrapTile)left).getTrap().equals("grass") &&
					!stepped.get(left(orientation,currentC))) {
				forkNewTrace(orientation,currentC);
				turnLeft();
				state=States.ExploringGrass;
				break;
			}else if(right.isType(Type.TRAP) &&
					((TrapTile)right).getTrap().equals("grass") &&
					!stepped.get(right(orientation,currentC))) {
				forkNewTrace(orientation,currentC);
				turnRight();
				state=States.ExploringGrass;
				break;
			}
			if(checkAhead(orientation,currentC) && !checkLeft(orientation,currentC) && !checkRight(orientation,currentC)) {
				//ahead is blocked while left and right are not
				forkNewTrace(orientation,currentC);
				turnLeft();
			}else if(checkAhead(orientation,currentC) && checkLeft(orientation,currentC) && !checkRight(orientation,currentC)) {
				//ahead and left are blocked while right is not
				turnRight();
			}else if(checkAhead(orientation,currentC) && checkRight(orientation,currentC) && !checkLeft(orientation,currentC)) {
				//ahead and right are blocked while left is not
				turnLeft();
			}else if(checkLeft(orientation,ahead(orientation,currentC)) &&
					!checkAhead(orientation,currentC) &&
					!checkLeft(orientation,currentC)) {
				//choosing from going ahead or left when left-front is blocked
				forkNewTrace(orientation,currentC);
			}else if(checkRight(orientation,ahead(orientation,currentC)) &&
					!checkAhead(orientation,currentC) &&
					!checkRight(orientation,currentC)) {
				//choosing from going ahead or right when right-front is blocked
				forkNewTrace(orientation,currentC);
			}else if(deadend(orientation,currentC)) {
				//entered a dead-end
				applyBrake();
				traceBack.peek().pop();
				state=States.BackTracking;
			}
			break;
		case ExploringGrass:
			if(checkAhead(orientation,currentC)) {
				applyBrake();
				state=States.BackTracking;
				if(ahead.isType(Type.TRAP) && ((TrapTile)ahead).getTrap().equals("lava")) {
					nextToLava.put(currentC, orientation);
					HPlosses.put(currentC, null);
					routeThroughLava.put(currentC, null);
				}
			}else if(ahead.isType(Type.ROAD)){
				traceBack.peek().push(currentC);
				state=States.Exploring;
			}else if(ahead.isType(Type.TRAP) && ((TrapTile)ahead).getTrap().equals("grass")) {
				traceBack.peek().push(currentC);
			}
			break;
		case BackTracking:
			if(currentTrace==null && !traceBack.empty()) {
				currentTrace=traceBack.pop();
			}else if(currentTrace==null && !traceBack.empty()){
				System.err.println("empty traceBack plus null currentTrace when back tracking");
			}
			if(currentTrace.empty()) {
				if(traceBack.empty()) {
					state=States.NextArea;
				}else {
					stepped.replace(ahead(orientation,currentC),Boolean.FALSE);
					state=States.Exploring;
				}
				currentTrace=null;
				applyBrake();
			}else{
				towardsNextCoor(orientation,currentC,currentTrace.pop());
			}
			break;
		case NextArea:
			if(HPlosses.containsValue(null)) {
				for(Coordinate c:nextToLava.keySet()) {
					if(!traverseLava(c,nextToLava.get(c))) {
						nextToLava.remove(c);
						HPlosses.remove(c);
						routeThroughLava.remove(c);
					}
				}
			}
			if(nextToLava.isEmpty()) {
				state=States.FindKey;
				break;
			}else {
				if(currentTrace==null) {
					currentTrace=P2P(currentC,routeThroughLava.get(sortedLeastKey(HPlosses)).peek(),orientation);
				}
				if(currentTrace.size()==1){//manually switch to ThroughLava state so that the car is right on the entry point of lava
					if(ahead(orientation,currentC).equals(currentTrace.peek())) applyForwardAcceleration();
					else if(left(orientation,currentC).equals(currentTrace.peek())) turnLeft();
					else if(left(orientation,currentC).equals(currentTrace.peek())) turnLeft();
					else System.err.println("tracing failed:next coordinate not next to current one");
					state=States.ThroughLava;
					currentTrace=routeThroughLava.get(sortedLeastKey(HPlosses));
					currentTrace.pop();//get rid of the starting point outside of lava
				}if(currentTrace.size()>1) {
					towardsNextCoor(orientation, currentC, currentTrace.pop());
				}
			}
			break;
		case ThroughLava:
			break;
		case FindKey:
			break;
		case GetKey:
			break;
		case GetOut:
			break;
		}
	}
	
	/**
	 * using BFS to find the shortest route between 2 points
	 * code adopted from Mr giolekva's answer on https://stackoverflow.com/questions/1579399/shortest-path-fewest-nodes-for-unweighted-graph
	 * @param start
	 * @param dest
	 * @param orientation
	 * @return a trace of coordinates
	 */
	private Stack<Coordinate> P2P(Coordinate start, Coordinate dest, Direction orientation) {
		ArrayList<Coordinate> vis=new ArrayList<>();
		HashMap<Coordinate,Coordinate> pre=new HashMap<>();
		Stack<Coordinate> route = new Stack<>();
	    Queue<Coordinate> q = new LinkedList<>();
	    Queue<Direction> orientationQ= new LinkedList<>();
	    Coordinate current = start;
	    Direction currentO=orientation;
	    q.add(current);
	    orientationQ.add(currentO);
	    vis.add(current);
	    while(!q.isEmpty()){
	        current = q.remove();
	        currentO=orientationQ.remove();
	        if (current.equals(dest)){
	            break;
	        }else{
	        	HashMap<Coordinate,Direction> possibleWays=possibleWays(current,currentO);
	            for(Coordinate node : possibleWays.keySet()){
	                if(!vis.contains(node)){
	                    q.add(node);
	                    orientationQ.add(possibleWays.get(node));
	                    vis.add(node);
	                    pre.put(node, current);
	                }
	            }
	        }
	    }
	    if (!current.equals(dest)){
	        return null;
	    }
	    for(Coordinate node = dest; node != null; node = pre.get(node)) {
	        route.push(node);
	    }
	    return route;
	}

	/**
	 * helper method for P2P, find all the next possible coordinates that can be reached from current
	 * @param current
	 * @param orientation
	 * @return
	 */
	private HashMap<Coordinate, Direction> possibleWays(Coordinate current, Direction orientation) {
		HashMap<Coordinate,Direction> ways=new HashMap<>();
		if(map.get(current) instanceof GrassTrap) {
			if(!map.get(ahead(orientation,current)).isType(Type.WALL) &&
					!(map.get(ahead(orientation,current)) instanceof LavaTrap) ||
							safeLava.contains(ahead(orientation,current))) {
				ways.put(ahead(orientation,current), orientation);
			}
		}else {
			if(!map.get(ahead(orientation,current)).isType(Type.WALL) &&
					!(map.get(ahead(orientation,current)) instanceof LavaTrap) ||
							safeLava.contains(ahead(orientation,current))) {
				ways.put(ahead(orientation,current), orientation);
			}else if(!map.get(left(orientation,current)).isType(Type.WALL) &&
					!(map.get(left(orientation,current)) instanceof LavaTrap) ||
					safeLava.contains(left(orientation,current))) {
				ways.put(left(orientation,current), WorldSpatial.changeDirection(orientation, RelativeDirection.LEFT));
			}else if(!map.get(right(orientation,current)).isType(Type.WALL) &&
					!(map.get(right(orientation,current)) instanceof LavaTrap) ||
					safeLava.contains(right(orientation,current))) {
				ways.put(right(orientation,current), WorldSpatial.changeDirection(orientation, RelativeDirection.RIGHT));
			}
		}
		return ways;
	}

	/**
	 * find the first key in a HashMap<Coordinate,double> that has the least value associated
	 * @param map
	 * @return first key with the least value
	 */
	private Coordinate sortedLeastKey(HashMap<Coordinate, Double> map) {
		double lowest=0;
		Coordinate key=null;
		for(Coordinate c:map.keySet()) {
			if(lowest==0) {
				lowest=(map.get(c));
				key=c;
			}else if(map.get(c)<lowest) {
				lowest=(map.get(c));
				key=c;
			}
		}
		return key;
	}

	/**
	 * fork a new trace in the traceBack stack, this will happen whenever the car makes a turn or enters a grass 
	 * or entering a one-way during exploring 
	 * @param orientation
	 * @param currentC
	 */
	private void forkNewTrace(Direction orientation, Coordinate currentC) {
		traceBack.peek().pop();
		traceBack.push(new Stack<>());
		traceBack.peek().push(back(orientation,currentC));
		traceBack.peek().push(currentC);
	}

	/**
	 * Using breadth first search algorithm to find the route from a point, going through a barrier of lava, to a new sub area
	 * if there is no such route, remove this point from the HashMaps.
	 * code adopted from Mr giolekva's answer on https://stackoverflow.com/questions/1579399/shortest-path-fewest-nodes-for-unweighted-graph
	 * @param start: starting coordinate, a coordinate next to a trap in the explored area
	 * @param originalOrientation: the orientation when entering the traps from this point
	 * @param HPloss: total HPloss after passing through
	 * @return a stack of coordinates, indicating the route
	 */
	public boolean traverseLava(Coordinate start,Direction originalOrientation){
		ArrayList<Coordinate> vis=new ArrayList<>();
		HashMap<Coordinate,Coordinate> pre=new HashMap<>();
	    Stack<Coordinate> route = new Stack<>();
	    Queue<Coordinate> q = new LinkedList<>();
	    Queue<Direction> orientationQ= new LinkedList<>();
	    Coordinate current = start;
	    Direction orientation=originalOrientation;
	    q.add(current);
	    orientationQ.add(orientation);
	    vis.add(current);
	    double HPloss=0;
	    while(!q.isEmpty()){
	        current = q.poll();
	        orientation=orientationQ.poll();
	        if (detected.get(current) &&
	        		!stepped.get(current) &&
	        		!map.get(current).isType(Type.WALL) &&
	        		!(map.get(current) instanceof LavaTrap)){
	            break;
	        }else{
	        	HashMap<Coordinate,Direction> possibleWays=possibleLavaWays(current,orientation);
	            for(Coordinate node : possibleWays.keySet()){
	                if(!vis.contains(node)){
	                    q.add(node);
	                    orientationQ.add(possibleWays.get(node));
	                    vis.add(node);
	                    pre.put(node, current);
	                }
	            }
	        }
	    }
	    if (!(detected.get(current) &&
        		!stepped.get(current) &&
        		!map.get(current).isType(Type.WALL) &&
        		!(map.get(current) instanceof LavaTrap))){
	        return false;
	    }
	    int lowest=0;
	    for(Coordinate c:pre.keySet()) {
	    	Stack<Coordinate> temp=new Stack<>();
	    	if(detected.get(c) &&
	        		!stepped.get(c) &&
	        		!map.get(c).isType(Type.WALL) &&
	        		!(map.get(c) instanceof LavaTrap)) {
	    		for(Coordinate node = c; node != null; node = pre.get(node)) {
	    	        temp.push(node);
	    	    }
	    		if(lowest==0) {
	    			lowest=temp.size();
	    			route=temp;
	    		}else if(lowest>temp.size()) {
	    			lowest=temp.size();
	    			route=temp;
	    		}
	    	}
	    }
	    HPloss=LavaHP*(route.size()-2);//the first and last coordinates are outside of lava
	    HPlosses.replace(start, HPloss);
	    routeThroughLava.replace(start, route);
	    return true;
	}

	/**
	 * helper method for traverseLava(), specially for traversing through lava
	 * @param current:current coordinate when traversing the lava
	 * @param orientation:current orientation when arriving at this orientation
	 * @return all the next possible coordinates reachable from current and the orientation.
	 */
	private HashMap<Coordinate,Direction> possibleLavaWays(Coordinate current,Direction orientation) {
		HashMap<Coordinate,Direction> possibleWays=new HashMap<>();
		if (!(map.get(current) instanceof LavaTrap)){//at the start, entering lava first
			possibleWays.put(ahead(orientation,current),orientation);
		}else {//health or lava
			if(!map.get(ahead(orientation,current)).isType(Type.WALL) && 
					!stepped.get(ahead(orientation,current))) possibleWays.put(ahead(orientation,current),orientation);
			if(!map.get(left(orientation,current)).isType(Type.WALL) && 
					!stepped.get(left(orientation,current))) possibleWays.put(left(orientation,current),WorldSpatial.changeDirection(orientation, RelativeDirection.LEFT));
			if(!map.get(right(orientation,current)).isType(Type.WALL) && 
					!stepped.get(right(orientation,current))) possibleWays.put(right(orientation,current),WorldSpatial.changeDirection(orientation, RelativeDirection.RIGHT));
		}
		return possibleWays;
	}

	private void towardsNextCoor(Direction orientation, Coordinate currentC, Coordinate next) {
		if(getSpeed()==0){
			if(back(orientation,currentC).equals(next)) {
				applyReverseAcceleration();
			}else if(ahead(orientation,currentC).equals(next)) {
				applyForwardAcceleration();
			}else {
				System.err.println("tracing failed:next coordinate not next to current one");
			}
		}else {
			if(ahead(orientation,currentC).equals(next)) {
				applyForwardAcceleration();//same as do nothing
			}else if(left(orientation,currentC).equals(next)) {
				turnLeft();
			}else if(right(orientation,currentC).equals(next)) {
				turnRight();
			}else if(back(orientation,currentC).equals(next)) {
				applyReverseAcceleration();//same as do nothing
			}
			else {
				System.err.println("tracing failed:next coordinate not next to current one");
			}
		}
	}

	/**
	 * the following methods gives the coordinates on the 4 directions of the current coordination
	 * @param orientation
	 * @param currentC
	 * @return
	 */
	private Coordinate ahead(Direction orientation,Coordinate currentC) {
		Coordinate newC=null;
		switch (orientation) {
		case EAST:
			newC=new Coordinate(currentC.x+1,currentC.y);
			break;
		case WEST:
			newC=new Coordinate(currentC.x-1,currentC.y);
			break;
		case NORTH:
			newC=new Coordinate(currentC.x,currentC.y+1);
			break;
		case SOUTH:
			newC=new Coordinate(currentC.x,currentC.y-1);
			break;
		}
		return newC;
	}
	private Coordinate back(Direction orientation,Coordinate currentC) {
		Coordinate newC=null;
		switch (orientation) {
		case EAST:
			newC=new Coordinate(currentC.x-1,currentC.y);
			break;
		case WEST:
			newC=new Coordinate(currentC.x+1,currentC.y);
			break;
		case NORTH:
			newC=new Coordinate(currentC.x,currentC.y-1);
			break;
		case SOUTH:
			newC=new Coordinate(currentC.x,currentC.y+1);
			break;
		}
		return newC;
	}
	private Coordinate left(Direction orientation,Coordinate currentC) {
		Coordinate newC=null;
		switch (orientation) {
		case EAST:
			newC=new Coordinate(currentC.x,currentC.y+1);
			break;
		case WEST:
			newC=new Coordinate(currentC.x,currentC.y-1);
			break;
		case NORTH:
			newC=new Coordinate(currentC.x-1,currentC.y);
			break;
		case SOUTH:
			newC=new Coordinate(currentC.x+1,currentC.y);
			break;
		}
		return newC;
	}
	private Coordinate right(Direction orientation,Coordinate currentC) {
		Coordinate newC=null;
		switch (orientation) {
		case EAST:
			newC=new Coordinate(currentC.x,currentC.y-1);
			break;
		case WEST:
			newC=new Coordinate(currentC.x,currentC.y+1);
			break;
		case NORTH:
			newC=new Coordinate(currentC.x+1,currentC.y);
			break;
		case SOUTH:
			newC=new Coordinate(currentC.x-1,currentC.y);
			break;
		}
		return newC;
	}
	
	/**
	 * the following methods check whether the tile on each of the directions of the current tile is a wall
	 * or a lava trap or has been stepped on
	 * @param orientation
	 * @param currentC
	 * @return
	 */
	private boolean checkAhead(Direction orientation,Coordinate currentC) {
		Coordinate ahead=ahead(orientation,currentC);
		if(map.get(ahead).isType(Type.WALL) || 
				(map.get(ahead).isType(Type.TRAP) && ((TrapTile)map.get(ahead)).getTrap().equals("lava")) || 
				stepped.get(ahead)) {
			return true;
		}
		return false;
	}
	private boolean checkLeft(Direction orientation,Coordinate currentC) {
		Coordinate left=left(orientation,currentC);
		if(map.get(left).isType(Type.WALL) || 
				(map.get(left).isType(Type.TRAP) && ((TrapTile)map.get(left)).getTrap().equals("lava")) || 
				stepped.get(left)) {
			return true;
		}
		return false;
	}
	private boolean checkRight(Direction orientation,Coordinate currentC) {
		Coordinate right=right(orientation,currentC);
		if(map.get(right).isType(Type.WALL) || 
				(map.get(right).isType(Type.TRAP) && ((TrapTile)map.get(right)).getTrap().equals("lava")) || 
				stepped.get(right)) {
			return true;
		}
		return false;
	}
	
	/**
	 * Check if the car has encountered a dead-end, i.e. has the tiles on all 3 directions to be wall, 
	 * lava trap, or stepped on already.
	 * @param orientation
	 * @param currentC
	 * @return
	 */
	private boolean deadend(Direction orientation,Coordinate currentC) {
		if(checkAhead(orientation,currentC)
				&& checkLeft(orientation,currentC)
				&& checkRight(orientation,currentC)) {
			return true;
		}
		return false;
	}

}
