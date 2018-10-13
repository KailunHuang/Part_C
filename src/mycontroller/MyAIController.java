package mycontroller;

import controller.CarController;
import tiles.MapTile;
import utilities.Coordinate;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import world.Car;

public class MyAIController extends CarController{
	
	HashMap<Coordinate, MapTile> map = getMap();
	HashMap<Coordinate, Boolean> detected = new HashMap<Coordinate, Boolean>();
	HashMap<Coordinate, Boolean> Track = new HashMap<Coordinate, Boolean>();
	
	
	
	public MyAIController(Car car) {
		super(car);
		for (Coordinate x : map.keySet()) {
			detected.put(x, false);
		}
	}

	@Override
	public void update() {
		// TODO Auto-generated method stub
		
	}
	
	
}
