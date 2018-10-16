package mycontroller;

import java.util.HashMap;
import java.util.Stack;

import controller.CarController;
import tiles.MapTile;
import tiles.TrapTile;
import tiles.MapTile.Type;
import utilities.Coordinate;
import world.Car;
import world.WorldSpatial;
import world.WorldSpatial.Direction;

public class MyAIController extends CarController{
	private HashMap<Coordinate,MapTile> map;
	private HashMap<Coordinate,Boolean> detected;
	private HashMap<Coordinate,Boolean> stepped;
	private Stack<Stack<Coordinate>> traceBack;
	private Stack<Coordinate> currentTrace;
	private enum States {Beginning,Exploring,BackTracking,P2P,ThroughTrap,GetKey};
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
		switch (state){
		case Beginning:
			if(checkAhead(orientation,currentC)) {
				applyReverseAcceleration();
				stepped.replace(currentC, Boolean.FALSE);
			}else if(getSpeed()<0) {
				applyBrake();
			}else {
				applyForwardAcceleration();
				traceBack.push(new Stack<>());
				traceBack.peek().push(currentC);
				state=States.Exploring;
			}
			break;
		case Exploring:
			if(getSpeed()==0) {
				applyForwardAcceleration();
				break;
			}
			traceBack.peek().push(currentC);
			if(checkAhead(orientation,currentC) && !deadend(orientation,currentC)) {
				traceBack.peek().pop();
				traceBack.push(new Stack<>());
				traceBack.peek().push(back(orientation,currentC));
				traceBack.peek().push(currentC);
				if(!checkLeft(orientation,currentC)) {
					turnLeft();
				}else {
					turnRight();
				}
			}else if(deadend(orientation,currentC)) {
				applyBrake();
				state=States.BackTracking;
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
					state=States.P2P;
				}else {
					stepped.replace(ahead(orientation,currentC),Boolean.FALSE);
					state=States.Exploring;
				}
				currentTrace=null;
				applyBrake();
			}else{
				Coordinate back=currentTrace.pop();
				System.out.print(back);
				System.out.println(currentC);
				getback(orientation,currentC,back);
			}
			break;
		}
	}

	private void getback(Direction orientation, Coordinate currentC, Coordinate back) {
		if(back(orientation,currentC).equals(back)) {
			applyReverseAcceleration();
		}else if(left(orientation,currentC).equals(back)) {
			turnLeft();
		}else if(right(orientation,currentC).equals(back)) {
			turnRight();
		}else { // currentC==back
			// do nothing
		}
	}

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
	
	private boolean checkAhead(Direction orientation,Coordinate currentC) {
		Coordinate ahead=ahead(orientation,currentC);
		if(map.get(ahead).isType(Type.WALL) || map.get(ahead).isType(Type.TRAP) || stepped.get(ahead)) {
			return true;
		}
		return false;
	}
	
	private boolean checkLeft(Direction orientation,Coordinate currentC) {
		Coordinate left=left(orientation,currentC);
		if(map.get(left).isType(Type.WALL) || map.get(left).isType(Type.TRAP) || stepped.get(left)) {
			return true;
		}
		return false;
	}
	
	private boolean checkRight(Direction orientation,Coordinate currentC) {
		Coordinate right=right(orientation,currentC);
		if(map.get(right).isType(Type.WALL) || map.get(right).isType(Type.TRAP) || stepped.get(right)) {
			return true;
		}
		return false;
	}
	
	private boolean deadend(Direction orientation,Coordinate currentC) {
		if(checkAhead(orientation,currentC)
				&& checkLeft(orientation,currentC)
				&& checkRight(orientation,currentC)) {
			return true;
		}
		return false;
	}

}
