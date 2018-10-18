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
	private HashMap<Integer,HashMap<Coordinate,Direction>> closeToKey;// All the entry points of lava that can see a certain key
	private HashMap<Integer,HashMap<Coordinate,Double>> keyHPlosses;// Required HP to get a certain key from a certain point
	private HashMap<Integer,HashMap<Coordinate,Stack<Coordinate>>> routeForKey;// The routes each entry would take to get a key
	private Stack<Coordinate> healthPoints;// All the HealthTraps that has been stepped on, in a stack so that the nearest one should be used
	private double restoreCost;//the HP we need to get to the last HealthTrap 
	private Stack<Coordinate> backFromKey;
	private ArrayList<Coordinate> exit;
	private int currentKey=0;
	private enum States {Beginning,Exploring,ExploringGrass,BackTracking,NextArea,TurnAround,ThroughLava,FindKey,GetKey,BackingFromKey,Restoring};
	private States state;
	private States lastState;

	public MyAIController(Car car) {
		super(car);
		map=getMap();
		detected=new HashMap<>();
		stepped=new HashMap<>();
		exit=new ArrayList<>();
		for (Coordinate c:map.keySet()) {
			if (map.get(c).isType(Type.TRAP)){
				detected.put(c, Boolean.FALSE);
			}else if(map.get(c).isType(Type.FINISH)){
				exit.add(c);
				detected.put(c, Boolean.TRUE);
			}else {
				detected.put(c,Boolean.TRUE);
			}
			stepped.put(c, Boolean.FALSE);
		}
		state=States.Beginning;
		lastState=null;
		traceBack=new Stack<>();
		currentTrace=null;
		nextToLava=new HashMap<>();
		HPlosses=new HashMap<>();
		routeThroughLava=new HashMap<>();
		safeLava=new ArrayList<>();
		closeToKey=new HashMap<>();
		keyHPlosses=new HashMap<>();
		routeForKey=new HashMap<>();
		healthPoints=new Stack<>();
		restoreCost=0;
		backFromKey=new Stack<>();
		for(int i=1;i<=numKeys();i++) {
			closeToKey.put(i, new HashMap<>());
			keyHPlosses.put(i, new HashMap<>());
			routeForKey.put(i, new HashMap<>());
		}
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
		System.out.println(state);
		switch (state){
		case Beginning:
			if(checkAhead(orientation,currentC)) {
				applyReverseAcceleration();
				stepped.replace(currentC, Boolean.FALSE);
				break;
			}else if(getSpeed()>0) {//can only stay still or move backwards in Beginning state
				applyBrake();
			}
			//resting while ahead is not blocked, then start exploring
			if(lastState==null) {
				traceBack.push(new Stack<>());
				traceBack.peek().push(currentC);
			}
			lastState=States.Beginning;
			if(map.get(currentC) instanceof GrassTrap) {
				state=States.ExploringGrass;
			}else {
				state=States.Exploring;
			}
			break;
		case Exploring:
			if(getSpeed()==0) {
				applyForwardAcceleration();
				break;
			}
			traceBack.peek().push(currentC);
			if(map.get(currentC) instanceof HealthTrap) {
				healthPoints.push(currentC);
				if(getHealth()<MaxHP) {
					applyBrake();
					state=States.Restoring;
					lastState=States.Exploring;
				}
			}
			if((ahead instanceof LavaTrap && !safeLava.contains(ahead(orientation,currentC))) ||
					(left instanceof LavaTrap && !safeLava.contains(left(orientation,currentC))) ||
					(right instanceof LavaTrap && !safeLava.contains(right(orientation,currentC))) &&
					!nextToLava.containsKey(currentC)) {
				HPlosses.put(currentC, null);
				routeThroughLava.put(currentC, null);
				if(ahead instanceof LavaTrap && !safeLava.contains(ahead(orientation,currentC))) {
					if(canSeeKey(currentView).size()>0) addKey(canSeeKey(currentView),currentC,orientation);
					nextToLava.put(currentC, orientation);
				}else if(left instanceof LavaTrap && !safeLava.contains(left(orientation,currentC))) {
					if(canSeeKey(currentView).size()>0) addKey(canSeeKey(currentView),currentC,WorldSpatial.changeDirection(orientation, RelativeDirection.LEFT));
					nextToLava.put(currentC, WorldSpatial.changeDirection(orientation, RelativeDirection.LEFT));
				}else if(right instanceof LavaTrap && !safeLava.contains(right(orientation,currentC))) {
					if(canSeeKey(currentView).size()>0) addKey(canSeeKey(currentView),currentC,WorldSpatial.changeDirection(orientation, RelativeDirection.RIGHT));
					nextToLava.put(currentC, WorldSpatial.changeDirection(orientation, RelativeDirection.RIGHT));
				}
			}
			if(!checkAhead(orientation,currentC)) {
				if(ahead.isType(Type.TRAP) &&
						((TrapTile)ahead).getTrap().equals("grass") &&
						!stepped.get(ahead(orientation,currentC))) {
					forkNewTrace(orientation,currentC);
					state=States.ExploringGrass;
					lastState=States.Exploring;
					break;
				}
			}
			if(!checkLeft(orientation,currentC)) {
				if(left.isType(Type.TRAP) &&
						((TrapTile)left).getTrap().equals("grass") &&
						!stepped.get(left(orientation,currentC))) {
					forkNewTrace(orientation,currentC);
					turnLeft();
					state=States.ExploringGrass;
					lastState=States.Exploring;
					break;
				}
			}
			if(!checkAhead(orientation,currentC)) {
				if(right.isType(Type.TRAP) &&
						((TrapTile)right).getTrap().equals("grass") &&
						!stepped.get(right(orientation,currentC))) {
					forkNewTrace(orientation,currentC);
					turnRight();
					state=States.ExploringGrass;
					lastState=States.Exploring;
					break;
				}
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
				lastState=States.Exploring;
			}
			break;
		case ExploringGrass:
			if(checkAhead(orientation,currentC)) {
				applyBrake();
				state=States.BackTracking;
				lastState=States.ExploringGrass;
				if(ahead instanceof LavaTrap && !safeLava.contains(ahead(orientation,currentC))) {
					nextToLava.put(currentC, orientation);
					HPlosses.put(currentC, null);
					routeThroughLava.put(currentC, null);
				}
			}else if(ahead.isType(Type.ROAD)){
				traceBack.peek().push(currentC);
				state=States.Exploring;
				lastState=States.ExploringGrass;
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
					state=States.FindKey;
					lastState=States.BackTracking;
				}else {
					stepped.replace(ahead(orientation,currentC),Boolean.FALSE);
					state=States.Exploring;
					lastState=States.BackTracking;
				}
				currentTrace=null;
				applyBrake();
			}else{
				while(towardsNextCoor(orientation,currentC,currentTrace.pop())==0);
			}
			break;
		case NextArea:
			if(HPlosses.containsValue(null)) {
				for(Coordinate c:nextToLava.keySet()) {
					if(!traverseLava(c,nextToLava.get(c))) {
						HPlosses.replace(c,MaxHP);
						routeThroughLava.replace(c,new Stack<>());
					}
				}
			}
			if(getKeys().size()==numKeys() && stepped.get(exit.get(0))) {//got all keys while been through the exit
				if(currentTrace==null) {
					currentTrace=P2P(currentC,exit.get(0),orientation);
				}
				if(currentTrace.size()>0) {
					while(towardsNextCoor(orientation, currentC, currentTrace.pop())==0);
				}
			}else {//still needs to go to the next area
				if(currentTrace==null) {
					currentTrace=P2P(currentC,routeThroughLava.get(sortedLeastKey(HPlosses)).peek(),orientation);
				}
				if(currentTrace.size()==1){
					towardsNextCoor(orientation, currentC, currentTrace.pop());
					state=States.ThroughLava;
					lastState=States.NextArea;
					currentTrace=routeThroughLava.get(sortedLeastKey(HPlosses));
					//from this point, the chosen way of going through lava is excluded from our maps
					nextToLava.remove(sortedLeastKey(HPlosses));
					routeThroughLava.remove(sortedLeastKey(HPlosses));
					HPlosses.remove(sortedLeastKey(HPlosses));
				}else if(currentTrace.size()>1) {
					//first decide whether the car is going backward and whether it can turn around without losing HP
					if(currentTrace.peek().equals(back(orientation,currentC)) &&
							(map.get(currentC).isType(Type.ROAD) || map.get(currentC) instanceof HealthTrap)){
						if(left.isType(Type.ROAD) || left instanceof HealthTrap){
							state=States.TurnAround;
							lastState=States.NextArea;
							turnLeft();
							break;
						}else if(right.isType(Type.ROAD) || right instanceof HealthTrap) {
							state=States.TurnAround;
							lastState=States.NextArea;
							turnRight();
							break;
						}
					}
					while(towardsNextCoor(orientation, currentC, currentTrace.pop())==0);
				}
			}
			break;
		case TurnAround:
			if(getSpeed()>0) {
				applyBrake();
			}else {
				applyForwardAcceleration();
				state=States.NextArea;
				lastState=States.TurnAround;
			}
			break;
		case ThroughLava:
			if(getSpeed()==0) {
				applyForwardAcceleration();
				break;
			}
			if(map.get(currentC) instanceof LavaTrap) safeLava.add(currentC);
			if(currentTrace.size()>1) {
				while(towardsNextCoor(orientation,currentC,currentTrace.pop())==0);
			}else {
				towardsNextCoor(orientation, currentC, currentTrace.peek());
				if(map.get(currentTrace.peek()) instanceof GrassTrap) {
					lastState=States.ThroughLava;
					state=States.ExploringGrass;
				}else {
					state=States.Exploring;
					lastState=States.ThroughLava;
				}
				//preparing for a new round of exploring
				traceBack.push(new Stack<>());
				currentTrace=null;
			}
			break;
		case FindKey:
			if(currentTrace==null) {//initialization
				if(closeToKey.keySet().size()>0) {
					for(int key:closeToKey.keySet()) {
						if(!closeToKey.get(key).isEmpty()) {
							for(Coordinate c:keyHPlosses.get(key).keySet()) {
								if(!findKey(c,closeToKey.get(key).get(c),key)) {
									keyHPlosses.get(key).replace(c,MaxHP);
									routeForKey.get(key).replace(c,new Stack<>());
								}
							}
						}
					}
				}
				if(canGetKey().size()==0) {
					state=States.NextArea;
					lastState=States.FindKey;
					break;
				}else {
					currentKey=canGetKey().get(0);
					currentTrace=P2P(currentC,routeForKey.get(currentKey).get(sortedLeastKey(keyHPlosses.get(currentKey))).peek(),orientation);
				}
			}else if(currentTrace.size()==1){
				backFromKey.push(currentC);
				towardsNextCoor(orientation, currentC, currentTrace.pop());
				state=States.GetKey;
				lastState=States.FindKey;
				currentTrace=routeForKey.get(currentKey).get(sortedLeastKey(keyHPlosses.get(currentKey)));
			}else if(currentTrace.size()>1) {
				backFromKey.push(currentC);
				while(towardsNextCoor(orientation, currentC, currentTrace.pop())==0);
			}
			break;
		case GetKey:
			backFromKey.push(currentC);
			if(currentTrace.empty()) {
				currentTrace=backFromKey;
				applyBrake();
				closeToKey.remove(currentKey);
				keyHPlosses.remove(currentKey);
				routeForKey.remove(currentKey);
				state=States.BackingFromKey;
				lastState=States.FindKey;
			}else {
				while(towardsNextCoor(orientation, currentC, currentTrace.pop())==0);
			}
			break;
		case BackingFromKey:
			System.out.println(currentTrace);
			if(currentTrace.empty()) {
				//has returned to one tile before the entry point of lava, so turn the entry to stepped since it's outside of lava
				//replace all stepped lava in this state to false for later path finding in NextArea state
				for(Coordinate c:backFromKey) {
					if(map.get(c) instanceof LavaTrap) {
						stepped.replace(c, false);
					}
				}
				backFromKey.clear();
				stepped.replace(ahead(orientation,currentC), true);
				applyBrake();
				currentTrace=null;
				state=States.Restoring;
				lastState=States.BackingFromKey;
			}else {
				while(towardsNextCoor(orientation,currentC,currentTrace.pop())==0);
			}
			break;
		case Restoring:
			if(healthPoints.empty() || getHealth()==MaxHP) {
				if(lastState==States.BackingFromKey) {
					state=States.FindKey;
					lastState=States.Restoring;
				}else if(lastState==States.Exploring) {
					state=States.Beginning;
					lastState=States.Restoring;
				}
				break;
			}
			if(map.get(currentC) instanceof HealthTrap && getHealth()<MaxHP) {
				currentTrace=null;
				applyBrake();
				break;
			}
			if(currentTrace==null) {
				currentTrace=P2P(currentC,healthPoints.peek(),orientation);
				for(Coordinate c:currentTrace) {
					if(map.get(c) instanceof LavaTrap) restoreCost-=LavaHP;
					else if(map.get(c) instanceof HealthTrap) break;
				}
			}else{//currentTrace can never be empty since the second if will capture that situation faster than this if
				if(restoreCost>=getHealth()) {
					state=States.FindKey;
					lastState=States.Restoring;
					break;
				}
				while(towardsNextCoor(orientation, currentC, currentTrace.pop())==0);
			}
			break;
		}
	}
	
	private ArrayList<Integer> canGetKey() {
		ArrayList<Integer> keys=new ArrayList<>();
		if(closeToKey.keySet().isEmpty()) return keys;
		else {
			for(int key:keyHPlosses.keySet()) {
				if(!keyHPlosses.get(key).isEmpty()) {
					if(keyHPlosses.get(key).get(sortedLeastKey(keyHPlosses.get(key)))<MaxHP) {
						keys.add(key);
					}
				}
			}
			return keys;
		}
	}

	private void addKey(ArrayList<Integer> canSeeKey, Coordinate currentC, Direction orientation) {
		for(int key:canSeeKey) {
			if(closeToKey.keySet().contains(key)) {
				if(!closeToKey.get(key).containsKey(currentC)) {
					closeToKey.get(key).put(currentC, orientation);
					keyHPlosses.get(key).put(currentC, null);
					routeForKey.get(key).put(currentC, null);
				}
			}
		}
	}

	private ArrayList<Integer> canSeeKey(HashMap<Coordinate, MapTile> currentView) {
		ArrayList<Integer> keys=new ArrayList<>();
		for(Coordinate c:currentView.keySet()) {
			if(currentView.get(c) instanceof LavaTrap &&
					((LavaTrap)currentView.get(c)).getKey()>0) {
				keys.add(((LavaTrap)currentView.get(c)).getKey());
			}
		}
		return keys;
	}

	/**
	 * using BFS to find the shortest route between 2 points. If 2 points are the same, this method will try to leave and get back to the start
	 * point so that the car will be in motion when arriving at the destination, in case a turn is needed right after that.
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
	    HashMap<Coordinate,Direction> possibleWays=possibleWays(start,current,currentO);
        for(Coordinate node : possibleWays.keySet()){
            if(!vis.contains(node)){
                q.add(node);
                orientationQ.add(possibleWays.get(node));
                vis.add(node);
                pre.put(node, current);
            }
        }
	    while(!q.isEmpty()){
	        current = q.remove();
	        currentO=orientationQ.remove();
	        if (current.equals(dest)){
	            break;
	        }else{
	        	possibleWays=possibleWays(start,current,currentO);
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
	    for(Coordinate node = dest; node != start; node = pre.get(node)) {
	        route.push(node);
	    }
	    route.push(start);
	    return route;
	}

	/**
	 * helper method for P2P, find all the next possible coordinates that can be reached from current. if current is
	 * the starting point, it behaves like on grass.
	 * @param current
	 * @param orientation
	 * @return
	 */
	private HashMap<Coordinate, Direction> possibleWays(Coordinate start,Coordinate current, Direction orientation) {
		HashMap<Coordinate,Direction> ways=new HashMap<>();
		if(map.get(current) instanceof GrassTrap || current.equals(start)) {
			if(!map.get(ahead(orientation,current)).isType(Type.WALL) &&
					(!(map.get(ahead(orientation,current)) instanceof LavaTrap) ||
							safeLava.contains(ahead(orientation,current)))) {
				ways.put(ahead(orientation,current), orientation);
			}
			if(!map.get(back(orientation,current)).isType(Type.WALL) &&
					(!(map.get(back(orientation,current)) instanceof LavaTrap) ||
							safeLava.contains(back(orientation,current)))) {
				ways.put(back(orientation,current), orientation);
			}
		}else {
			if(!map.get(ahead(orientation,current)).isType(Type.WALL) &&
					(!(map.get(ahead(orientation,current)) instanceof LavaTrap) ||
							safeLava.contains(ahead(orientation,current)))) {
				ways.put(ahead(orientation,current), orientation);
			}
			if(!map.get(left(orientation,current)).isType(Type.WALL) &&
					(!(map.get(left(orientation,current)) instanceof LavaTrap) ||
							safeLava.contains(left(orientation,current)))) {
				ways.put(left(orientation,current), WorldSpatial.changeDirection(orientation, RelativeDirection.LEFT));
			}
			if(!map.get(right(orientation,current)).isType(Type.WALL) &&
					(!(map.get(right(orientation,current)) instanceof LavaTrap) ||
							safeLava.contains(right(orientation,current)))) {
				ways.put(right(orientation,current), WorldSpatial.changeDirection(orientation, RelativeDirection.RIGHT));
			}
			if(!map.get(back(orientation,current)).isType(Type.WALL) &&
					(!(map.get(back(orientation,current)) instanceof LavaTrap) ||
							safeLava.contains(back(orientation,current)))) {
				ways.put(back(orientation,current), orientation);
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
	    HPloss=-LavaHP*(route.size()-2);//the first and last coordinates are outside lava
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
		}else {
			if(!map.get(ahead(orientation,current)).isType(Type.WALL) && 
					!stepped.get(ahead(orientation,current))) possibleWays.put(ahead(orientation,current),orientation);
			if(!map.get(left(orientation,current)).isType(Type.WALL) && 
					!stepped.get(left(orientation,current))) possibleWays.put(left(orientation,current),WorldSpatial.changeDirection(orientation, RelativeDirection.LEFT));
			if(!map.get(right(orientation,current)).isType(Type.WALL) && 
					!stepped.get(right(orientation,current))) possibleWays.put(right(orientation,current),WorldSpatial.changeDirection(orientation, RelativeDirection.RIGHT));
		}
		return possibleWays;
	}

	/**
	 * key finding version of traverseLava()
	 * @param start
	 * @param originalOrientation
	 * @return
	 */
	public boolean findKey(Coordinate start,Direction originalOrientation,int key){
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
	        if ((map.get(current) instanceof LavaTrap) && ((LavaTrap)map.get(current)).getKey()==key){
	            break;
	        }else{
	        	HashMap<Coordinate,Direction> possibleWays=possibleKeyWays(current,orientation);
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
	    if (!((map.get(current) instanceof LavaTrap) && ((LavaTrap)map.get(current)).getKey()==key)){
	        return false;
	    }
	    int lowest=0;
	    for(Coordinate c:pre.keySet()) {
	    	Stack<Coordinate> temp=new Stack<>();
	    	if((map.get(c) instanceof LavaTrap) && ((LavaTrap)map.get(c)).getKey()==key) {
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
	    HPloss=-LavaHP*(route.size());
	    keyHPlosses.get(key).replace(start, HPloss);
	    routeForKey.get(key).replace(start, route);
	    return true;
	}
	
	private HashMap<Coordinate, Direction> possibleKeyWays(Coordinate current, Direction orientation) {
		HashMap<Coordinate,Direction> possibleWays=new HashMap<>();
		if (!(map.get(current) instanceof LavaTrap)){//at the start, entering lava first
			possibleWays.put(ahead(orientation,current),orientation);
		}else {
			if(map.get(ahead(orientation,current)) instanceof LavaTrap) possibleWays.put(ahead(orientation,current),orientation);
			if(map.get(left(orientation,current)) instanceof LavaTrap) possibleWays.put(left(orientation,current),WorldSpatial.changeDirection(orientation, RelativeDirection.LEFT));
			if(map.get(right(orientation,current)) instanceof LavaTrap) possibleWays.put(right(orientation,current),WorldSpatial.changeDirection(orientation, RelativeDirection.RIGHT));
		}
		return possibleWays;
	}

	/**
	 * move the car from currentC to the next coordinate in a trace of route. There shouldn't be
	 * 2 same coordinates in 1 route.
	 * @param orientation
	 * @param currentC
	 * @param next
	 */
	private int towardsNextCoor(Direction orientation, Coordinate currentC, Coordinate next) {
		if(getSpeed()==0){
			if(back(orientation,currentC).equals(next)) {
				applyReverseAcceleration();
				return 1;
			}else if(ahead(orientation,currentC).equals(next)) {
				applyForwardAcceleration();
				return 1;
			}else if(currentC.equals(next)){
				return 0;
			}
			else {
				System.err.println("tracing failed,currentC:"+currentC.toString()+" next:"+next.toString());
				return -1;
			}
		}else {
			if(ahead(orientation,currentC).equals(next)) {
				return 1; 
			}else if(left(orientation,currentC).equals(next)) {
				turnLeft();
				return 1;
			}else if(right(orientation,currentC).equals(next)) {
				turnRight();
				return 1;
			}else if(back(orientation,currentC).equals(next)) {
				return 1;
			}else if(currentC.equals(next)) {
				return 0;
			}else {
				System.err.println("tracing failed,currentC:"+currentC.toString()+" next:"+next.toString());
				return -1;
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
		if(!map.keySet().contains(ahead)) return true;
		if(map.get(ahead).isType(Type.WALL) || 
				(map.get(ahead) instanceof LavaTrap) || 
				stepped.get(ahead)) {
			return true;
		}
		return false;
	}
	private boolean checkLeft(Direction orientation,Coordinate currentC) {
		Coordinate left=left(orientation,currentC);
		if(!map.keySet().contains(left)) return true;
		if(map.get(left).isType(Type.WALL) || 
				(map.get(left) instanceof LavaTrap) || 
				stepped.get(left)) {
			return true;
		}
		return false;
	}
	private boolean checkRight(Direction orientation,Coordinate currentC) {
		Coordinate right=right(orientation,currentC);
		if(!map.keySet().contains(right)) return true;
		if(map.get(right).isType(Type.WALL) || 
				(map.get(right) instanceof LavaTrap) || 
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
