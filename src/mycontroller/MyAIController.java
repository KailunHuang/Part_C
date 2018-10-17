package mycontroller;

import controller.CarController;
import tiles.MapTile;
import tiles.TrapTile;
import tiles.LavaTrap;
import utilities.Coordinate;

import java.util.HashMap;
import java.util.Map;

import org.omg.CORBA.Current;

import java.util.ArrayList;
import world.Car;
import world.WorldSpatial;

public class MyAIController extends CarController{
	
	HashMap<Coordinate, MapTile> map = getMap();
	HashMap<Coordinate, Boolean> detected = new HashMap<Coordinate, Boolean>();
	HashMap<Coordinate, Boolean> Track = new HashMap<Coordinate, Boolean>();
	ArrayList<Coordinate> FoundKeys = new ArrayList<Coordinate>();
	
	
	private boolean KeyAccessbility = false;
	public enum State {Exploring, PickingUpKey, Recovery, WayOut, DeadEnd,OnGrass};
	private Coordinate health = new Coordinate(0,0);
	
	private boolean isFollowingWall = false; // This is set to true when the car starts sticking to a wall.
	
	private final static int WALLSENSITIVITY =  1;
	
	// Car Speed to move at
	private final int CAR_MAX_SPEED = 1;
	
	State CurrentState;
	
	
	public MyAIController(Car car) {
		super(car);
		CurrentState = State.Exploring;
		for (Coordinate x : map.keySet()) {
			detected.put(x, false);
		}
	}

	@Override
	public void update() {
		// TODO Auto-generated method stub
		HashMap<Coordinate, MapTile> currentView = getView();
		System.out.println(CurrentState);
//		System.out.println(checkDeadEnd(getOrientation(), currentView));
//		System.out.println(this.getPosition());
//		System.out.println(this.getHealth());
		
		for (Coordinate x:currentView.keySet()) {
			if (detected.get(x)!=null) {
				if (detected.get(x)==false) {
					detected.put(x, true);
					map.put(x, currentView.get(x));
				}
			}
		}
		
		switch (CurrentState) {
		case Exploring: exploring(currentView); break;
		case PickingUpKey: pickingupKey(currentView); break;
		case Recovery:recover(health, currentView); break;
		case WayOut: wayOut(); break; 
		case DeadEnd: leaveDeadEnd(currentView);break;
		case OnGrass: moveOnGrass(currentView);break;
		}	
		
		
		System.out.print("\n");
	}
	
	public void exploring(HashMap<Coordinate, MapTile> currentView) {
		// checkStateChange();		
		Coordinate currentPosition = new Coordinate(getPosition());
		if (checkDeadEnd(getOrientation(), currentView)) {
			CurrentState = State.DeadEnd;
			applyBrake();
		}else if(findKey(currentView)!=null) {
			Coordinate key = findKey(currentView);
			if(!canIMoveThere(currentPosition,key,currentView)) {
				simpleMove(currentView, currentPosition);
			}else {
				CurrentState = State.PickingUpKey;
			}
		}
		else if ((findHealth(currentPosition, currentView)!=null) && (getHealth() < 100)) {
			health = findHealth(currentPosition, currentView);
			
			//System.out.println("should I get the health "+shouldIGetTheHealth(getOrientation(), health, currentPosition, currentView));
			if(!shouldIGetTheHealth(getOrientation(), health, currentPosition, currentView)){
				simpleMove(currentView, currentPosition);
			}else {
				CurrentState = State.Recovery;
			}
		}
		else if(isOnGrass(currentPosition)) {
			CurrentState = State.OnGrass;
		}
		else{
			simpleMove(currentView, currentPosition);
			
		}
		
	}
	
	public void simpleMove(HashMap<Coordinate, MapTile> currentView, Coordinate currentPosition) {
		WorldSpatial.Direction orientation = getOrientation();
		
		if(getSpeed() < CAR_MAX_SPEED && CurrentState ==State.Exploring){       // Need speed to turn and progress toward the exit
			applyForwardAcceleration();   // Tough luck if there's a wall in the way
		}
		
	
	
		if (checkWallAhead(getOrientation(), currentView)) {
			if(checkFollowingWall(getOrientation(),currentView)) {
				turnRight();
			}else if(checkRightWall(getOrientation(), currentView)) {
				turnLeft();
			}else {
				turnRight();
			}
		}else {
			if (!checkFollowingWall(orientation,currentView) && !checkRightWall(orientation, currentView)){
//				Coordinate x = findCloestWall(currentPosition, currentView);
				switch(orientation) {
					case EAST: 
						if (checkNorthWest(currentView)) {
							turnLeft();
						}else if (!checkNorthWest(currentView) && !checkNorthEast(currentView) &&
								!checkSouthEast(currentView) && checkSouthWest(currentView)) {
							turnRight();
						}
						break;
					case WEST:
						if (checkSouthEast(currentView)) {
							turnLeft();
						}else if (!checkNorthWest(currentView) && checkNorthEast(currentView) &&
								!checkSouthEast(currentView) && !checkSouthWest(currentView)) {
							turnRight();
						}
						break;
					case NORTH:
						if (checkSouthWest(currentView)) {
							turnLeft();
						}else if (!checkNorthWest(currentView) && !checkNorthEast(currentView) &&
								checkSouthEast(currentView) && !checkSouthWest(currentView)) {
							turnRight();
						}
						break;
					case SOUTH:
						if (checkNorthEast(currentView)) {
							turnLeft();
						}else if (checkNorthWest(currentView) && !checkNorthEast(currentView) &&
								!checkSouthEast(currentView) && !checkSouthWest(currentView)) {
							turnRight();
						}
						break;
				}
				
//				if (!checkNorthWest(currentView) && !checkNorthEast(currentView) &&
//						!checkSouthEast(currentView) && !checkSouthWest(currentView)) {
//					movePointToPoint(x, currentPosition, currentView, "wall");
//				}
				
			}
			
		}
	
	}
	
	
	public void pickingupKey(HashMap<Coordinate, MapTile> currentView) {
		Coordinate currentPosition = new Coordinate(getPosition());
		Coordinate key = findKey(currentView);
		System.out.println("key is null "+key);
		//System.out.println("can i move there "+canIMoveThere(currentPosition,key));
		if (key == null) {
			turnIfWallAhead(getOrientation(),currentView);
			CurrentState = State.Exploring;
		}else {
			System.out.println("can i move there "+canIMoveThere(currentPosition,key,currentView));
			if(!canIMoveThere(currentPosition,key,currentView)) {
				turnIfWallAhead(getOrientation(),currentView);
				CurrentState = State.Exploring;
			}else {
				movePointToPoint(key, currentPosition,currentView, "key");
			}
		}
		
	}
	
	public void recover(Coordinate health, HashMap<Coordinate, MapTile> currentView) {
		Coordinate currentPosition = new Coordinate(getPosition());

		System.out.println("health coordi "+health);
		if (health == null) {
			turnIfWallAhead(getOrientation(),currentView);
			CurrentState = State.Exploring;
		}else {
			if (!shouldIGetTheHealth(getOrientation(), health, currentPosition, currentView)) {
				turnIfWallAhead(getOrientation(),currentView);
				CurrentState = State.Exploring;
			}else if(){
				
			}else {
				movePointToPoint(health, currentPosition, currentView, "health");
			}
		}
		
	}
	
	public void wayOut() {
		
	}
	
	public void moveOnGrass(HashMap<Coordinate, MapTile> currentView) {
		boolean goback  = false;
		Coordinate currentPosition = new Coordinate(getPosition());
		if(getSpeed() < CAR_MAX_SPEED && !goback){       // Need speed to turn and progress toward the exit
			applyForwardAcceleration();   // Tough luck if there's a wall in the way
		}
		
		if (isOnGrass(currentPosition)) {
			if(checkWallAhead(getOrientation(), currentView)) {
				applyBrake();
				goback = true;
			}
			
			if(getSpeed() <CAR_MAX_SPEED && goback) {
				applyReverseAcceleration();
			}
		}else {
			CurrentState = State.Exploring;
		}
		
	}
	
	public void turnIfWallAhead(WorldSpatial.Direction orientation, HashMap<Coordinate, MapTile> currentView) {
		if (checkWallAhead(orientation, currentView)) {
			if(checkFollowingWall(orientation,currentView)) {
				turnRight();
			}else if(checkRightWall(orientation, currentView)) {
				turnLeft();
			}else {
				turnRight();
			}
		}
	}
	
	public void leaveDeadEnd(HashMap<Coordinate, MapTile> currentView) {
		if(getSpeed() < CAR_MAX_SPEED) {
			applyReverseAcceleration();
		}
		
		WorldSpatial.Direction orientation = getOrientation();
		System.out.println(checkOutOfDeadEnd(currentView));
		if (checkOutOfDeadEnd(currentView)) {
			switch(orientation) {
			case EAST:
				if (checkNorth(currentView, WALLSENSITIVITY)) {
					turnLeft();
				}else {
					turnRight();
				}
				break;
			case WEST:
				if (checkNorth(currentView, WALLSENSITIVITY)) {
					turnRight();
				}else {
					turnLeft();
				}
				break;
			case NORTH:
				if (checkWest(currentView, WALLSENSITIVITY)) {
					turnRight();
				}else {
					turnLeft();
				}
				break;
			case SOUTH: 
				if (checkWest(currentView, WALLSENSITIVITY)) {
					turnLeft();
				}else {
					turnRight();
				}
				break;
			}

			CurrentState = State.Exploring;
			applyBrake();
		}
	}
	
	
	/**
	 * Check if you have a wall in front of you!
	 * @param orientation the orientation we are in based on WorldSpatial
	 * @param currentView what the car can currently see
	 * @return
	 */
	private boolean checkWallAhead(WorldSpatial.Direction orientation, HashMap<Coordinate, MapTile> currentView){
		switch(orientation){
		case EAST:
			return checkEast(currentView,WALLSENSITIVITY);
		case NORTH:
			return checkNorth(currentView,WALLSENSITIVITY);
		case SOUTH:
			return checkSouth(currentView,WALLSENSITIVITY);
		case WEST:
			return checkWest(currentView,WALLSENSITIVITY);
		default:
			return false;
		}
	}
	
	/**
	 * Check if the wall is on your left hand side given your orientation
	 * @param orientation
	 * @param currentView
	 * @return
	 */
	//it also called check left wall
	private boolean checkFollowingWall(WorldSpatial.Direction orientation, HashMap<Coordinate, MapTile> currentView) {
		
		switch(orientation){
		case EAST:
			return checkNorth(currentView,WALLSENSITIVITY);
		case NORTH:
			return checkWest(currentView,WALLSENSITIVITY);
		case SOUTH:
			return checkEast(currentView,WALLSENSITIVITY);
		case WEST:
			return checkSouth(currentView,WALLSENSITIVITY);
		default:
			return false;
		}	
	}
	
	public boolean checkRightWall(WorldSpatial.Direction orientation, HashMap<Coordinate, MapTile> currentView) {
		switch(orientation) {
		case EAST:
			return checkSouth(currentView,WALLSENSITIVITY);
		case NORTH:
			return checkEast(currentView,WALLSENSITIVITY);
		case SOUTH:
			return checkWest(currentView,WALLSENSITIVITY);
		case WEST:
			return checkNorth(currentView,WALLSENSITIVITY);
		default:
			return false;
		}
	}
	
	/**
	 * Method below just iterates through the list and check in the correct coordinates.
	 * i.e. Given your current position is 10,10
	 * checkEast will check up to wallSensitivity amount of tiles to the right.
	 * checkWest will check up to wallSensitivity amount of tiles to the left.
	 * checkNorth will check up to wallSensitivity amount of tiles to the top.
	 * checkSouth will check up to wallSensitivity amount of tiles below.
	 */
	public boolean checkEast(HashMap<Coordinate, MapTile> currentView, int wallSensitivity){
		// Check tiles to my right
		Coordinate currentPosition = new Coordinate(getPosition());
		for(int i = 0; i <= wallSensitivity; i++){
			MapTile tile = currentView.get(new Coordinate(currentPosition.x+i, currentPosition.y));
			
			if(tile.isType(MapTile.Type.WALL) || tile.isType(MapTile.Type.FINISH)){
				return true;
			}else if(tile.isType(MapTile.Type.TRAP)) {
				TrapTile trap = (TrapTile)tile;
				if (trap.getTrap().equals("mud")) {
					return true;
				}
			}
		}
		return false;
	}

	
	public boolean checkWest(HashMap<Coordinate,MapTile> currentView,int wallSensitivity){
		// Check tiles to my left
		Coordinate currentPosition = new Coordinate(getPosition());
		for(int i = 0; i <= wallSensitivity; i++){
			MapTile tile = currentView.get(new Coordinate(currentPosition.x-i, currentPosition.y));
			
			if(tile.isType(MapTile.Type.WALL) || tile.isType(MapTile.Type.FINISH)){
				return true;
			}else if(tile.isType(MapTile.Type.TRAP)) {
				TrapTile trap = (TrapTile)tile;
				if (trap.getTrap().equals("mud")) {
					return true;
				}
			}
		}
		return false;
	}
	
	public boolean checkNorth(HashMap<Coordinate,MapTile> currentView, int wallSensitivity){
		// Check tiles to towards the top
		Coordinate currentPosition = new Coordinate(getPosition());
		for(int i = 0; i <= wallSensitivity; i++){
			MapTile tile = currentView.get(new Coordinate(currentPosition.x, currentPosition.y+i));
			
			if(tile.isType(MapTile.Type.WALL) || tile.isType(MapTile.Type.FINISH)){
				return true;
			}else if(tile.isType(MapTile.Type.TRAP)) {
				TrapTile trap = (TrapTile)tile;
				if (trap.getTrap().equals("mud")) {
					return true;
				}
			}
		}
		return false;
	}
	
	public boolean checkSouth(HashMap<Coordinate,MapTile> currentView, int wallSensitivity){
		// Check tiles towards the bottom
		Coordinate currentPosition = new Coordinate(getPosition());
		for(int i = 0; i <= wallSensitivity; i++){
			MapTile tile = currentView.get(new Coordinate(currentPosition.x, currentPosition.y-i));
			
			if(tile.isType(MapTile.Type.WALL) || tile.isType(MapTile.Type.FINISH)){
				return true;
			}else if(tile.isType(MapTile.Type.TRAP)) {
				TrapTile trap = (TrapTile)tile;
				if (trap.getTrap().equals("mud")) {
					return true;
				}
			}
		}
		return false;
	}
	
	public boolean checkNorthWest(HashMap<Coordinate, MapTile> currentView) {
		Coordinate currentPosition = new Coordinate(getPosition());
		MapTile tile = currentView.get(new Coordinate(currentPosition.x-1, currentPosition.y+1));
		if(tile.isType(MapTile.Type.WALL) || tile.isType(MapTile.Type.FINISH)){
			return true;
		}else if(tile.isType(MapTile.Type.TRAP)) {
			TrapTile trap = (TrapTile)tile;
			if (trap.getTrap().equals("mud")) {
				return true;
			}
		}
		return false;
	}
	
	public boolean checkNorthEast(HashMap<Coordinate, MapTile> currentView) {
		Coordinate currentPosition = new Coordinate(getPosition());
		MapTile tile = currentView.get(new Coordinate(currentPosition.x+1, currentPosition.y+1));
		if(tile.isType(MapTile.Type.WALL) || tile.isType(MapTile.Type.FINISH)){
			return true;
		}else if(tile.isType(MapTile.Type.TRAP)) {
			TrapTile trap = (TrapTile)tile;
			if (trap.getTrap().equals("mud")) {
				return true;
			}
		}
		return false;
	}
	
	public boolean checkSouthWest(HashMap<Coordinate, MapTile> currentView) {
		Coordinate currentPosition = new Coordinate(getPosition());
		MapTile tile = currentView.get(new Coordinate(currentPosition.x-1, currentPosition.y-1));
		if(tile.isType(MapTile.Type.WALL) || tile.isType(MapTile.Type.FINISH)){
			return true;
		}else if(tile.isType(MapTile.Type.TRAP)) {
			TrapTile trap = (TrapTile)tile;
			if (trap.getTrap().equals("mud")) {
				return true;
			}
		}
		return false;
	}
	
	public boolean checkSouthEast(HashMap<Coordinate, MapTile> currentView) {
		Coordinate currentPosition = new Coordinate(getPosition());
		MapTile tile = currentView.get(new Coordinate(currentPosition.x+1, currentPosition.y-1));
		if(tile.isType(MapTile.Type.WALL) || tile.isType(MapTile.Type.FINISH)){
			return true;
		}else if(tile.isType(MapTile.Type.TRAP)) {
			TrapTile trap = (TrapTile)tile;
			if (trap.getTrap().equals("mud")) {
				return true;
			}
		}
		return false;
	}
	
	public boolean checkDeadEnd(WorldSpatial.Direction orientation, HashMap<Coordinate, MapTile> currentView) {
		//Coordinate currentPosition = new Coordinate(getPosition());
		switch (orientation) {
		case EAST: 
			return (eastDeadEnd(currentView)); 
		case WEST:
			return (westDeadEnd(currentView)); 
		case NORTH: 
			return (northDeadEnd(currentView)); 
		case SOUTH:
			return (southDeadEnd(currentView)); 
		}
		return false;
	}
	
	
	//return true if as least three direction of the car is free
	public boolean checkOutOfDeadEnd(HashMap<Coordinate, MapTile> currentView) {
		
		if (!checkSouth(currentView, WALLSENSITIVITY) && !checkNorth(currentView, WALLSENSITIVITY)
					&& !checkEast(currentView, WALLSENSITIVITY)) {
			return true;
		}else if(!checkSouth(currentView, WALLSENSITIVITY) && !checkNorth(currentView, WALLSENSITIVITY)
		&& !checkWest(currentView, WALLSENSITIVITY)){
			return true;
		}else if (!checkEast(currentView, WALLSENSITIVITY) && !checkNorth(currentView, WALLSENSITIVITY)
				&& !checkWest(currentView, WALLSENSITIVITY)) {
			return true;
		}else if(!checkSouth(currentView, WALLSENSITIVITY) && !checkEast(currentView, WALLSENSITIVITY)
					&& !checkWest(currentView, WALLSENSITIVITY)) {
			return true;
		}else {
			return false;
		}
		
	}
	
	public boolean eastDeadEnd(HashMap<Coordinate, MapTile> currentView) {
		if (checkSouth(currentView, WALLSENSITIVITY) && checkNorth(currentView, WALLSENSITIVITY)
				&& checkEast(currentView, WALLSENSITIVITY)) {
			return true;
		}
		return false;
	}
	
	public boolean westDeadEnd(HashMap<Coordinate, MapTile> currentView) {
		if (checkSouth(currentView, WALLSENSITIVITY) && checkNorth(currentView, WALLSENSITIVITY)
				&& checkWest(currentView, WALLSENSITIVITY)) {
			return true;
		}
		return false;
	}
	
	public boolean northDeadEnd(HashMap<Coordinate, MapTile> currentView) {
		if (checkEast(currentView, WALLSENSITIVITY) && checkNorth(currentView, WALLSENSITIVITY)
				&& checkWest(currentView, WALLSENSITIVITY)) {
			return true;
		}
		return false;
	}
	
	public boolean southDeadEnd(HashMap<Coordinate, MapTile> currentView) {
		if (checkSouth(currentView, WALLSENSITIVITY) && checkEast(currentView, WALLSENSITIVITY)
				&& checkWest(currentView, WALLSENSITIVITY)) {
			return true;
		}
		return false;
	}
	
	public Coordinate findKey(HashMap<Coordinate, MapTile> currentView) {
		for (Coordinate x: currentView.keySet()) {
			MapTile tile = currentView.get(x);
			if(currentView.get(x).isType(MapTile.Type.TRAP)) {
				TrapTile trap = (TrapTile)tile;
				if (trap.getTrap().equals("lava")) {
					LavaTrap lava = (LavaTrap) trap;
					if (lava.getKey() > 0 && !FoundKeys.contains(x)) {
						//System.out.println(x);
						System.out.println(getPosition());
						return x;
					}
				}
			}
		}
		return null;
	}
	
	//is that possible from aim coordinate to current coordinate to  
	public boolean canIMoveThere(Coordinate aim_coordi, Coordinate current_coordi,HashMap<Coordinate, MapTile> currentView){
		//System.out.println("aim_coordi "+aim_coordi);
		//System.out.println("key "+current_coordi);
		if ((aim_coordi.y >= current_coordi.y) && (aim_coordi.x <= current_coordi.x)) {
			KeyAccessbility = !isThereWallBetweenTheTwoPoints(aim_coordi.x, current_coordi.x, aim_coordi.y, current_coordi.y);
			return (KeyAccessbility);
		}else if ((aim_coordi.y <= current_coordi.y) && (aim_coordi.x <= current_coordi.x)){
			KeyAccessbility = !isThereWallBetweenTheTwoPoints(aim_coordi.x, current_coordi.x, current_coordi.y, aim_coordi.y);
			return (KeyAccessbility);
		}else if((aim_coordi.y >= current_coordi.y) && (aim_coordi.x >= current_coordi.x)) {
			KeyAccessbility = !isThereWallBetweenTheTwoPoints(current_coordi.x, aim_coordi.x, aim_coordi.y, current_coordi.y);
			return (KeyAccessbility);
		}else if((aim_coordi.y <= current_coordi.y) && (aim_coordi.x >= current_coordi.x)) {
			KeyAccessbility = !isThereWallBetweenTheTwoPoints(current_coordi.x, aim_coordi.x, current_coordi.y, aim_coordi.y);
			return (KeyAccessbility);
		}else {
			KeyAccessbility = false;
			return KeyAccessbility; 
		}
	}
	
	public boolean shouldIGetTheHealth(WorldSpatial.Direction orientation, Coordinate aim_coordi, Coordinate current_coordi,HashMap<Coordinate, MapTile> currentView) {
		
		switch(orientation) {
		case EAST:
			if (aim_coordi.x < current_coordi.x) {
				return false;
			}else {
				return canIMoveThere(aim_coordi, current_coordi, currentView);
			}
		case WEST:
			if (aim_coordi.x > current_coordi.x) {
				return false;
			}else {
				return canIMoveThere(aim_coordi, current_coordi, currentView);
			}
		case NORTH:
			if (aim_coordi.y < current_coordi.y) {
				return false;
			}else {
				return canIMoveThere(aim_coordi, current_coordi, currentView);
			}
		case SOUTH:
			if (aim_coordi.y > current_coordi.y) {
				return false;
			}else {
				return canIMoveThere(aim_coordi, current_coordi, currentView);
			}
		}
		return false;
	}
	public boolean isThereWallBetweenTheTwoPoints(int left, int right, int up, int down) {
		for (int a = left; a <= right; a++) {
			for (int b = down; b <= up; b++) {
				MapTile tile = map.get(new Coordinate(a,b));
				if (tile.isType(MapTile.Type.WALL)) {
					return true;
				}if (tile.isType(MapTile.Type.TRAP)) {
					TrapTile trap = (TrapTile)tile;
					if (trap.getTrap().equals("mud")) {
						return true;
					}
				}
			}
		}
		return false; 
	}
	
	public void movePointToPoint(Coordinate aim_coordi, Coordinate current_coordi,HashMap<Coordinate, MapTile> currentView,
			String state) {
		
		WorldSpatial.Direction orientation = getOrientation();
		if ((aim_coordi.x == current_coordi.x) && (aim_coordi.y == current_coordi.y)) {
			if (state.equals("key")) {
				FoundKeys.add(aim_coordi);
				if (checkWallAhead(getOrientation(), currentView)) {
					if(checkFollowingWall(getOrientation(),currentView)) {
						turnRight();
					}else if(checkRightWall(getOrientation(), currentView)) {
						turnLeft();
					}else {
						turnRight();
					}
				}
			}else if(state.equals("health")) {
				System.out.println("checkWallAhead "+checkWallAhead(getOrientation(), currentView));
				if (checkWallAhead(getOrientation(), currentView)) {
					if(checkFollowingWall(getOrientation(),currentView)) {
						turnRight();
					}else if(checkRightWall(getOrientation(), currentView)) {
						turnLeft();
					}else {
						turnRight();
					}
				}else {
					turnLeft();
				}
				applyBrake();
			}
			
			CurrentState = State.Exploring;
		}
		
		if (aim_coordi.x < current_coordi.x) {
			switch(orientation) {
				case EAST: break;
				case WEST: break;
				case NORTH: turnLeft(); break;
				case SOUTH: turnRight();break;
			}
		}else if(aim_coordi.x > current_coordi.x) {
			switch(orientation) {
				case EAST: break;
				case WEST: break;
				case NORTH: turnRight(); break;
				case SOUTH: turnLeft();break;
			}
		}else {
			System.out.println("X axis equals");
			// when aim_coordi.x == current_coordi.x
			
			if (aim_coordi.y > current_coordi.y) {
				switch(orientation) {
					case EAST: turnLeft(); break;
					case WEST: turnRight(); break;
					case NORTH: break;
					case SOUTH: break;
				}
			}else if (aim_coordi.y < current_coordi.y){ 
				switch(orientation) {
					case EAST: turnRight(); break;
					case WEST: turnLeft();;break;
					case NORTH: break;
					case SOUTH: break;
				}
			}
		}
	}
	
	public Coordinate findHealth(Coordinate currentPosition, HashMap<Coordinate, MapTile> currentView) {
		ArrayList<Coordinate> list = new ArrayList<Coordinate>();
		ArrayList<Integer> distances = new ArrayList<Integer>(); 
		for (Coordinate x: currentView.keySet()) {
			MapTile tile = currentView.get(x);
			if(currentView.get(x).isType(MapTile.Type.TRAP)) {
				TrapTile trap = (TrapTile)tile;
				if (trap.getTrap().equals("health")) {
					list.add(x);
					int  distance = (Math.abs(x.x-currentPosition.x)+Math.abs(x.y-currentPosition.y));
					distances.add(distance);
				}
			}
		}
		
		if (distances.isEmpty()) {
			return null;
		}else {
			int max = 0; 
			int index = 0; 
			for (int i = 0; i < distances.size(); i++) {
				if (distances.get(i)>max) {
					max = distances.get(i);
					index = i;
				}
			}
			return list.get(index);
		}
	}
	
	public boolean isOnGrass(Coordinate currentPosition) {
		MapTile tile = map.get(currentPosition);
		if (tile.isType(MapTile.Type.TRAP)) {
			TrapTile trap = (TrapTile)tile;
			if (trap.getTrap().equals("grass")) {
				return true;
			}else {
				return false;
			}
		}else {
			return false;
		}
	}
	
	public Coordinate findCloestWall(Coordinate currentPosition, HashMap<Coordinate, MapTile> currentView) {
		for (Coordinate x: currentView.keySet()) {
			MapTile tile = currentView.get(x);
			if(currentView.get(x).isType(MapTile.Type.WALL)) {
				return x;
			}
		}
		return null;
	}
	
	public boolean toNewPlace() {
		Coordinate currentPosition = new Coordinate(getPosition());
		WorldSpatial.Direction direction = this.getOrientation();
		
		MapTile rightTile, leftTile, forwardTile;
		Coordinate rightCoord=null, leftCoord=null, forwardCoord=null;
		
		switch (direction) {
		case NORTH:
			rightCoord = new Coordinate(currentPosition.x+5, currentPosition.y);
			leftCoord = new Coordinate(currentPosition.x-5, currentPosition.y);
			forwardCoord = new Coordinate(currentPosition.x, currentPosition.y+5);
			break;
		case EAST:
			rightCoord = new Coordinate(currentPosition.x, currentPosition.y-5);
			leftCoord = new Coordinate(currentPosition.x, currentPosition.y+5);
			forwardCoord = new Coordinate(currentPosition.x+5, currentPosition.y);
			break;
		case SOUTH:
			rightCoord = new Coordinate(currentPosition.x-5, currentPosition.y);
			leftCoord = new Coordinate(currentPosition.x+5, currentPosition.y);
			forwardCoord = new Coordinate(currentPosition.x, currentPosition.y-5);
			break;
		case WEST:
			rightCoord = new Coordinate(currentPosition.x, currentPosition.y+5);
			leftCoord = new Coordinate(currentPosition.x, currentPosition.y-5);
			forwardCoord = new Coordinate(currentPosition.x-5, currentPosition.y);
			break;
		default:
			break;
		}
		
		
		rightTile = map.get(rightCoord);
		leftTile = map.get(leftCoord);
		forwardTile = map.get(forwardCoord);

		
		if (rightTile != null && rightTile.isType(MapTile.Type.ROAD)) {
			if (detected.get(rightCoord) == false) {
				if (!detectObstacle(currentPosition, rightCoord)) {
					turnRight();
					return true;
				}
			}
		}
		else if (leftTile != null && leftTile.isType(MapTile.Type.ROAD)) {
			if (detected.get(leftCoord) == false) {
				if (!detectObstacle(currentPosition, leftCoord)) {
					turnLeft();
					return true;
				}
			}
		}
		else if (forwardTile != null && forwardTile.isType(MapTile.Type.ROAD)) {
			if (detected.get(forwardCoord) == false) {
				if (!detectObstacle(currentPosition, forwardCoord)) {
					// go straight
					return false;
				}
			}
		}
		return false;
		
	}
	
	
	public boolean detectObstacle(Coordinate start, Coordinate finish) {
		int xDiff = finish.x - start.x;
		int yDiff = finish.y - start.y;
		
		MapTile tile;
		
		// finish.x < start.x
		if (xDiff < 0) {
			for (int i = -1; i > xDiff; i--) {
				tile = map.get(new Coordinate(start.x + i, start.y));
				if (tile.isType(MapTile.Type.WALL)) {
					return true;
				}
				else if (tile.isType(MapTile.Type.TRAP) && ((TrapTile)tile).getTrap().equals("mud")) {			
					return true;
				}
			}
			return false;
		}
		
		// finish.x > start.x
		if (xDiff > 0) {
			for (int i = 1; i < xDiff; i++) {
				tile = map.get(new Coordinate(start.x + i, start.y));
				if (tile.isType(MapTile.Type.WALL)) {
					return true;
				}
				else if (tile.isType(MapTile.Type.TRAP) && ((TrapTile)tile).getTrap().equals("mud")) {			
					return true;
				}
			}
			return false;
		}
		
		// finish.y < start.y
		if (yDiff < 0) { 
			for (int i = -1; i > yDiff; i--) {
				tile = map.get(new Coordinate(start.x, start.y + i));
				if (tile.isType(MapTile.Type.WALL)) {
					return true;
				}
				else if (tile.isType(MapTile.Type.TRAP) && ((TrapTile)tile).getTrap().equals("mud")) {			
					return true;
				}
			}
			return false;
		}
		
		// finish.y > start.y
		if (yDiff > 0) {
			for (int i = 1; i < yDiff; i++) {
				tile = map.get(new Coordinate(start.x, start.y + i));
				if (tile.isType(MapTile.Type.WALL)) {
					return true;
				}
				else if (tile.isType(MapTile.Type.TRAP) && ((TrapTile)tile).getTrap().equals("mud")) {			
					return true;
				}
			}
			return false;
		}
		
		// default
		return false;
	}
	
}
