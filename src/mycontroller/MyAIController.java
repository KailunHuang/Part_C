package mycontroller;

import controller.CarController;
import tiles.MapTile;
import utilities.Coordinate;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import world.Car;
import world.WorldSpatial;

public class MyAIController extends CarController{
	
	HashMap<Coordinate, MapTile> map = getMap();
	HashMap<Coordinate, Boolean> detected = new HashMap<Coordinate, Boolean>();
	HashMap<Coordinate, Boolean> Track = new HashMap<Coordinate, Boolean>();
	public enum State {Exploring, PickingUpKey, Recovery, WayOut, DeadEnd};
	
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
		System.out.println(checkDeadEnd(getOrientation(), currentView));
		System.out.println(this.getPosition());
		System.out.println(this.getHealth());
		
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
		case PickingUpKey: pickingupKey(); break;
		case Recovery:recover(); break;
		case WayOut: wayOut(); break; 
		case DeadEnd: leaveDeadEnd(currentView);break;
		}
		
	}
	
	public void exploring(HashMap<Coordinate, MapTile> currentView) {
		// checkStateChange();		
		
		if(getSpeed() < CAR_MAX_SPEED && CurrentState ==State.Exploring){       // Need speed to turn and progress toward the exit
			applyForwardAcceleration();   // Tough luck if there's a wall in the way
		}
		
		if (checkDeadEnd(getOrientation(), currentView)) {
			CurrentState = State.DeadEnd;
			applyBrake();
		}else {
			
		
		}
	}
	
	public void pickingupKey() {
		
	}
	
	public void recover() {
		
	}
	
	public void wayOut() {
		
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
	public void leaveDeadEnd(HashMap<Coordinate, MapTile> currentView) {
		if(getSpeed() < CAR_MAX_SPEED) {
			applyReverseAcceleration();
		}
		
		WorldSpatial.Direction orientation = getOrientation();
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
				turnLeft();
			}else {
				turnRight();
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
		
		if (checkOutOfDeadEnd(currentView)) {
			CurrentState = State.Exploring;
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
			if(tile.isType(MapTile.Type.WALL)){
				return true;
			}
		}
		return false;
	}
	
	public boolean checkWest(HashMap<Coordinate,MapTile> currentView,int wallSensitivity){
		// Check tiles to my left
		Coordinate currentPosition = new Coordinate(getPosition());
		for(int i = 0; i <= wallSensitivity; i++){
			MapTile tile = currentView.get(new Coordinate(currentPosition.x-i, currentPosition.y));
			if(tile.isType(MapTile.Type.WALL)){
				return true;
			}
		}
		return false;
	}
	
	public boolean checkNorth(HashMap<Coordinate,MapTile> currentView, int wallSensitivity){
		// Check tiles to towards the top
		Coordinate currentPosition = new Coordinate(getPosition());
		for(int i = 0; i <= wallSensitivity; i++){
			MapTile tile = currentView.get(new Coordinate(currentPosition.x, currentPosition.y+i));
			if(tile.isType(MapTile.Type.WALL)){
				return true;
			}
		}
		return false;
	}
	
	public boolean checkSouth(HashMap<Coordinate,MapTile> currentView, int wallSensitivity){
		// Check tiles towards the bottom
		Coordinate currentPosition = new Coordinate(getPosition());
		for(int i = 0; i <= wallSensitivity; i++){
			MapTile tile = currentView.get(new Coordinate(currentPosition.x, currentPosition.y-i));
			if(tile.isType(MapTile.Type.WALL)){
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
	
	
}
