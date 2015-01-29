package com.tectonica.geo.server;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.routing.util.EncodingManager;
import com.tectonica.geo.common.model.DistTime;

@ApplicationScoped
public class GraphHopperManager
{
	private static final String GH_BASE = "/var/tectonica/geo";

	@Inject
	private Logger LOG;

	private GraphHopper gh;
	private DistTimeMatrix cache = new DistTimeMatrix();

	private final SimpleDateFormat sdf;

	public GraphHopperManager()
	{
		sdf = new SimpleDateFormat("HH:mm:ss");
		sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
	}

	private String dt(long time)
	{
		return sdf.format(new Date(time));
	}

	public void init()
	{
		File ghFolder = new File(GH_BASE + "/graph");
		ghFolder.mkdirs();
		String ghLoc = ghFolder.getAbsolutePath();
		LOG.info("GH Graph Folder: " + ghLoc);

		String osmPath = GH_BASE + "/map.osm.pbf";
		File osmFile = new File(osmPath);
		if (!osmFile.exists())
			throw new RuntimeException("missing " + osmFile.getAbsolutePath());

		LOG.info("Initializing GraphHoper..");

		gh = new GraphHopper() //
//				.forServer() // = in-memory + simplified-path
				.setStoreOnFlush(true) // = in-memory + yes/no storage
				.setOSMFile(osmPath) // for first time loading only, probably not for production
				.setGraphHopperLocation(ghLoc) //
				.setEncodingManager(new EncodingManager("motorcycle")) // need "|turnCosts=true"?
//				.setCHEnable(false) // still don't know what means..
		;

		gh.importOrLoad();

		LOG.info("Done initializing GraphHoper");
	}

	public DistTime distTime(double fromLat, double fromLng, double toLat, double toLng)
	{
		DistTime result = cache.get(fromLat, fromLng, toLat, toLng);
		
		if (result == null)
		{
			final GHResponse path = path(fromLat, fromLng, toLat, toLng);
			result = new DistTime();
			result.setDistM(path.getDistance());
			result.setTimeS(path.getMillis() / 1000L);
			result.setTimeText(dt(path.getMillis()));
			cache.put(fromLat, fromLng, toLat, toLng, result);
		}
		
		return result;
	}

	private GHResponse path(double fromLat, double fromLng, double toLat, double toLng)
	{
		GHResponse response = gh.route(//
				new GHRequest(fromLat, fromLng, toLat, toLng) //
//						.setVehicle("motorcycle") //
						.setWeighting("fastest") //
//						.setWeighting("shortest") //
//						.setAlgorithm(AlgorithmOptions.ASTAR) // goes with setCHEnable(false) 
				);

		if (response.hasErrors())
			throw new RuntimeException("Erroneus request");

		LOG.info("points   = " + response.getPoints().getSize());
		LOG.info("distance = " + response.getDistance() / 1000.0); // km
		LOG.info("time     = " + dt(response.getMillis()));

		return response;
	}
}
