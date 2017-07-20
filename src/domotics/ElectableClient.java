package domotics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Date;

import org.apache.avro.AvroRemoteException;
import org.apache.avro.ipc.SaslSocketServer;
import org.apache.avro.ipc.SaslSocketTransceiver;
import org.apache.avro.ipc.Server;
import org.apache.avro.ipc.Transceiver;
import org.apache.avro.ipc.specific.SpecificRequestor;
import org.apache.avro.ipc.specific.SpecificResponder;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.text.DecimalFormat;
import java.util.AbstractMap.SimpleEntry;

import protocols.avro.*;
import domotics.NetAddress;

/*
 * Class from which all possible servers (users, servers and fridges) inherit the server methods.
 * Contains code to hold elections, sync server data, and to decide when server timed out.
 * Contains the information required to become server (what clients are online, and what their addresses are).
 */
public abstract class ElectableClient extends Client implements Electable, Runnable {
	// TODO stand-in server can not register new clients, possibly impossible without multicast in current implementation
	public static final long COUNTDOWN = 4000;
	public static final long ELECTIONTIMEOUT = 6000;
	public static final int UPPERBOUND = 64*1024 + 1;
	
	public Integer ServerID = 6789;
	public String ServerIP = "127.0.0.1";
	public Integer OriginalServerID = 6789;
	public String OriginalServerIP = "127.0.0.1";
	
	private Map<String,Set<Integer> > clients = new ConcurrentHashMap<String,Set<Integer> >();
	private Object clientsLock = new Object();
	private Map<Integer,SimpleEntry<CharSequence,Boolean>> users = new ConcurrentHashMap<Integer,SimpleEntry<CharSequence,Boolean>>();
	private Map<CharSequence, CharSequence> addressList = new ConcurrentHashMap<CharSequence,CharSequence>();
	private List<Integer> savedLights = new Vector<Integer>();

	protected NetAddress selfID = null;
	private Pinger pingingobject = null;
	private Thread pingingEveryone = null;
	HashMap<Integer, Pinginfo> pingMap = new HashMap<Integer, Pinginfo>();
	private Timer syncTimer = new Timer();
	private SyncTimerTask synctimertask = new SyncTimerTask();
	private Thread serverThread = null;
	private Timer deadservertimer = new Timer();
	private ElectionTimerTask electiontimertask = new ElectionTimerTask();
	private double temperature = 0;
	protected Server server = null;
	private Object electionLock = new Object(); 
	private boolean electionBusy = false;
	public List<Double> temperatureHistory = new ArrayList<Double>();
	private double thermostatCounter = 0;
	private Thread timeKeeper = null;

	/* 
	 * stops a client from being the server when the original server gets back online.
	 */
	public void stopserver(){
		System.out.println("This is not a server anymore");
		log("cancelling pinger");
		pingingobject.stop();
		log("stopping server");
		server.close();
		log("cancelling synchronizer");
		synctimertask.cancel();
		this.start();
		this.standby(COUNTDOWN);
	}
	
	public void stop(){
		log("stop");
		if(server != null) {
			server.close();
		}
	}
	
	public void start(){}
	
	public Map<CharSequence,List<Integer> > ConvertClients(boolean reput){
		log("ConvertClient IN: " + clients);
		Map<CharSequence,List<Integer>> clientlist = new HashMap<>();
		for(String key: clients.keySet()){
			log("key = " + key);
			if(key.equalsIgnoreCase("server") && reput == true){
				clients.get(key).remove(this.getID());
				clients.get(key).add(this.OriginalServerID);				
			}
			if(key.equalsIgnoreCase("users") && reput == true && this.getName().equalsIgnoreCase("UserClient")){
				Set<Integer> users = clients.get(key);
				if(users == null) {
					users = new HashSet<Integer>();
					clients.put(key, users);
				}
				users.add(this.getID());
				log("have put self = " + this.getID() + " in set of users");
			}
		
			clientlist.put((CharSequence)key, new ArrayList<>(clients.get(key)) );
			
		}
		log("ConvertClient OUT: " + clients);
		log("ConvertClient RET: " + clientlist);
		return clientlist;
		
	}
	public Map<CharSequence,Map<CharSequence,Boolean>> ConvertUsers(boolean reput){
		Map<CharSequence,Map<CharSequence,Boolean>> userlist = new HashMap<>();
		for(int key: users.keySet()){
			CharSequence newkey = users.get(key).getKey();
			Boolean bool = users.get(key).getValue();
			HashMap<CharSequence,Boolean> tempmap = new HashMap<CharSequence,Boolean>();
			tempmap.put(newkey, bool);
			userlist.put(Integer.toString(key), tempmap);
		}
		return userlist;
	}
	
	public void SetElected(int _electedID){		
		int OwnID = this.getID();
		log("SetElected, ElectedID: " + _electedID + " Own ID: " + OwnID );
		if(OwnID == _electedID){
			System.out.println("You have won the elections and will become the server");
			stop();
			
			for(String key: clients.keySet()){
				for(Integer ID: clients.get(key)){
					if(ID == OwnID){
						clients.get(key).remove(OwnID);
						if(clients.get(key).isEmpty())
							clients.remove(key);
					}
				}
			}
			serverThread = new Thread(this);
			serverThread.start();
		}
		else{
			log(": stop server thread");
		}
	}
	
	//the method that copies the data the server must have, in case the server goes down
	public Void _sync(Map<CharSequence,List<Integer> > _clients,	Map<CharSequence,Map<CharSequence,Boolean>> _users, Map<CharSequence, CharSequence> _addresses,List<Integer> _lights){
		log("in sync; clients: "+ _clients + " users: " + _users+" addresses:"+_addresses+" lights:"+_lights);
		Map<CharSequence, CharSequence> quickCopy = new HashMap<CharSequence, CharSequence>();
		for(CharSequence key : _addresses.keySet()){
			quickCopy.put(key.toString(), _addresses.get(key));
		}
		

		HashMap<CharSequence,List<Integer>> clientlist = new HashMap<>(_clients);
		HashMap<CharSequence,Map<CharSequence,Boolean>> userlist = new HashMap<>(_users);
		clients.clear();
		for(CharSequence key: clientlist.keySet()){
			clients.put(key.toString(), new HashSet<>(clientlist.get(key)) );

		}
		
		for(CharSequence key: userlist.keySet()){
			
			int newkey = Integer.parseInt(key.toString()); //(users.get(key).getKey().toString());
			CharSequence otherkey = null;
			for( CharSequence key2: userlist.get(key).keySet()){
				otherkey = key2;
			}
			Boolean bool = userlist.get(key).get(otherkey);
			SimpleEntry<CharSequence,Boolean> tempmap = new SimpleEntry<CharSequence,Boolean>(otherkey, bool);
			users.put(newkey, tempmap);
		}
		addressList = quickCopy;

		savedLights = new Vector<Integer>(_lights);
		return null;
	}
	@Override
	public boolean Election(int LastID){
		log("ENTER ELECTION");
		try {
	
		int OwnID = this.getID();

		int nextInChain = UPPERBOUND;
		
		int min = UPPERBOUND;
		String _tempkey = "";
		for(String key: clients.keySet()){
			for(Integer ID: clients.get(key)){
				if(key.equalsIgnoreCase("fridges") || key.equalsIgnoreCase("users")){
					if(ID < nextInChain && ID > OwnID){
						nextInChain = ID;
						_tempkey = key;
					}
					if(ID<min){
						min = ID;
					}
				}
			}
		}
		if( nextInChain == UPPERBOUND){
			nextInChain = min;
		}
		if(nextInChain == UPPERBOUND){
			// I am alone
			SetElected(OwnID);
			return true;
		}
		else if(LastID == OwnID){
			// Elect myself
			SetElected(OwnID);
			Elected(OwnID, nextInChain);
			return true;
		}

		synchronized(electionLock) {
			if (electionBusy)
				return false;
			electionBusy = true; // set to false in finally
		}
		
		try {
			log("election: LastID: " + OwnID + " NextInChain: " + nextInChain );
			
			Transceiver client = null;
			try{
				CharSequence tempchain = Integer.toString(nextInChain);
				log("addresslist: " + addressList.size() );
				NetAddress IP = new NetAddress(nextInChain, addressList.get(tempchain).toString());
				client = new SaslSocketTransceiver(new InetSocketAddress(IP.getIP(),IP.getPort()));
				Electable.Callback proxy = (Electable.Callback) SpecificRequestor.getClient(Electable.Callback.class, client);
				if(OwnID > LastID){
					log("sending election "+OwnID+" to Ip: " + IP.getIP() + "," + IP.getPort());
					proxy.Election(OwnID);
				}
				else{
					log("sending election "+LastID+" to Ip: " + IP.getIP() + "," + IP.getPort());
					proxy.Election(LastID);
				}
	
			}
			catch(AvroRemoteException e){
					log("interrupted election");
					log("unexpected remote exception " + e);
			}
			catch(IOException e){
				log("exception during election: " + e);
				//nextinchain removing <key, nextinchain> from clients, in case the connection to that client has been lost
				RemoveFromClients(_tempkey, nextInChain);
			}
			finally{
				try {
					if(client != null)
						client.close();
				}
				catch (IOException e){}
			}
		} finally {
			electionBusy = false;
		}
		
		} finally{
			log("LEAVE ELECTION");
			//standby();
		}
		return true;
	}
	
	@Override
	public boolean Elected(int _ElectedID, int nextInChain){
		log("ENTER ELECTED");
		try {
			
		log("elected notification: LastID: " + _ElectedID + " NextInChain: " + nextInChain );
		
		if(nextInChain == this.getID()) {
			log("elected notification reached end of chain " + nextInChain);
			return false;
		}
		
		
		Transceiver client = null;
		try{
			NetAddress IP = new NetAddress(nextInChain,(addressList.get(Integer.toString(nextInChain))).toString());
			client = new SaslSocketTransceiver(new InetSocketAddress(IP.getIP(),IP.getPort()));
			Electable.Callback proxy = (Electable.Callback) SpecificRequestor.getClient(Electable.Callback.class, client);
			proxy.Elected(_ElectedID, nextInChain);
		}
		catch(IOException e){
			log("Throw in elected: " + e);
		}
		finally{
			try {
				if(client != null)
					client.close();
			}
			catch (IOException e){}
		}
		
		} finally {
			log("LEAVE ELECTED");
		}
		return false;
	}
	/*
	 * class that holds data on how many pings failed to receive a response.
	 */
	public class Pinginfo{
		public int missedpings = 0;
	}
	
	/*
	 * When timer fires, synchronizes data on all active clients between electable clients.
	 * For instance which users are in the house, or what lights are on.
	 */
	public class SyncTimerTask extends TimerTask{

		public void run(){
			if(clients.get("users") == null){
				Set<Integer> values = new HashSet<Integer>();
				clients.put("users", values);
			}
			if(clients.get("fridges") == null){
				Set<Integer> values = new HashSet<Integer>();
				clients.put("fridges", values);
			}
			log("preparing for syncing");
			Map<CharSequence,List<Integer>> clientlist = new HashMap<>();
			Map<CharSequence,Map<CharSequence,Boolean>> userlist = new HashMap<>();
			for(String key: clients.keySet()){
				clientlist.put((CharSequence)key, new ArrayList<>(clients.get(key)) );
			}
			for(int key: users.keySet()){
				
				CharSequence newkey = users.get(key).getKey();
				Boolean bool = users.get(key).getValue();
				HashMap<CharSequence,Boolean> tempmap = new HashMap<CharSequence,Boolean>();
				tempmap.put(newkey, bool);
				userlist.put(Integer.toString(key), tempmap);
			}
			
			log("syncing");
			Map<CharSequence, CharSequence> mapcopy = new HashMap<CharSequence, CharSequence>();
			for(CharSequence key: addressList.keySet()){
				mapcopy.put(key, addressList.get(key));
			}
			for(String key: clients.keySet()){
				for(Integer ID: clients.get(key)){
					Transceiver client = null;
					try{
						// NetAddress IP = new NetAddress(ID,String.valueOf(addressList.get(ID.toString())));
						NetAddress IP = new NetAddress(ID,addressList.get(ID.toString()).toString());
						if(IP.getIP() == null){
							continue;
						}
						switch (key) {
						case "users":
							log("syncing " + key + " " + ID);
							client = new SaslSocketTransceiver(new InetSocketAddress(IP.getIP(),IP.getPort()));
							User proxyU = (User) SpecificRequestor.getClient(User.class, client);
							proxyU._sync(clientlist, userlist,mapcopy,savedLights);
							break;
						case "fridges":
							log("syncing " + key + " " + ID);
							client = new SaslSocketTransceiver(new InetSocketAddress(IP.getIP(),IP.getPort()));
							SmartFridge proxyF = (SmartFridge) SpecificRequestor.getClient(SmartFridge.class,client);
							proxyF._sync(clientlist, userlist,mapcopy,savedLights);
							break;	
						}
					}
					catch(IOException e){
						log("sync IOException: "+e);
					} catch(Exception e){
						log("sync exception: " + e);
					} finally {
						if(client != null)
							try {
								client.close();
							} catch (IOException e) {}
					}
				}
			}
		}
		
	}
	
	/*
	 * Pinger object that checks what clients are online and responsive.
	 * Calls isAlive method of clients it 'pings'.
	 */
	public class Pinger implements Runnable{
		ElectableClient ptr;
		int sleeper= 3000;
		public boolean _run = true;
		int threshold = 3;
		
		public Pinger(ElectableClient electable){
			ptr = electable;
		}
		public void stop(){
			_run = false;
		}
		public void run(){
			while(_run){
				try{
					
				Thread.sleep(sleeper);
				
				}
				
				catch(InterruptedException e){}
				pingserver();
				int successes = 0;
				int fails = 0;
				log("keyset: " + clients.keySet());

				for(String key: clients.keySet()){
					log("iD: "+ clients.get(key));
					for(Integer ID: clients.get(key)){
						boolean runningsmooth = false;
						if(pingMap.get(ID) == null){
							Pinginfo newinfo = new Pinginfo();
							pingMap.put(ID, newinfo);
						}
						Transceiver client = null;
						try{
							NetAddress IP = new NetAddress(ID,addressList.get(ID.toString()).toString());
							log("ip: " + IP);
							if(IP.getIP() == null){
								continue;
							}
							client = new SaslSocketTransceiver(new InetSocketAddress(IP.getIP(),IP.getPort()));
							log("pinging " + key + " " + ID);
							switch (key) {
							case "server":
								DomServer proxyS = (DomServer) SpecificRequestor.getClient(DomServer.class, client);
								runningsmooth = proxyS.IsAlive(OriginalServerIP,OriginalServerID);
								break;
							case "users":
								User proxyU = (User) SpecificRequestor.getClient(User.class, client);
								runningsmooth = proxyU.IsAlive(ptr.selfID.getIPStr(), ptr.getID());
								break;
							case "lights":
								Light proxyL = (Light) SpecificRequestor.getClient(Light.class, client);
								runningsmooth = proxyL.IsAlive();
								break;
							case "fridges":
								SmartFridge proxyF = (SmartFridge) SpecificRequestor.getClient(SmartFridge.class,client);
								runningsmooth = proxyF.IsAlive(ptr.selfID.getIPStr(), ptr.getID());
								break;
							case "thermostat":
								Thermostat proxyT = (Thermostat) SpecificRequestor.getClient(Thermostat.class,client);
								runningsmooth = proxyT.IsAlive(ptr.selfID.getIPStr(), ptr.getID());
								log("temperature: " + temperature);
								break;
							}
							
							client.close();
							client = null;
							if(runningsmooth != true){
								throw new Exception("Notrunning smooth");
							}
							//successes++;
							
							
						}
						catch(Exception e){
							
							if(client != null){
								try{
									client.close();
								}
								catch(IOException e2){
									log("Warning: problem closing client after exception " + e2);
								}
							}
							//fails++;
							log("objectID: " + ID + " Pinger Error :" +e);
							e.printStackTrace();
							Pinginfo missping = pingMap.get(ID);
							missping.missedpings ++;
							if(missping.missedpings >= threshold){
								pingMap.remove(ID);
								clients.get(key).remove(ID);
								savedLights.remove(ID);
								if(clients.get(key).size() == 0){
									if (key != "server"){
										clients.remove(key);
									}
								}
								if(key == "users"){
									try{LeaveHouse(ID);}
									catch(IOException woops){
										log("User not in users,... huh?");
									}
								}
								
							}
							
						}
					}
				}
				log("Pinging called: successes: " + successes + " fails: " + fails);
			}
		}
	}
	//starts running the server
	public void run(){		
		NetAddress ID = selfID;
		if (clients.get("server") == null){
			Set<Integer> values = new HashSet<Integer>();
			clients.put("server", values);
		}
		if (clients.get("users") == null){
			Set<Integer> values = new HashSet<Integer>();
			clients.put("users", values);
		}
		if (clients.get("lights") == null){
			Set<Integer> values = new HashSet<Integer>();
			clients.put("lights", values);
		}
		if (clients.get("fridges") == null){
			Set<Integer> values = new HashSet<Integer>();
			clients.put("fridges", values);
		}
		if (clients.get("thermostat") == null){
			Set<Integer> values = new HashSet<Integer>();
			clients.put("thermostat", values);
		}
		if(!clients.get("server").isEmpty() ){
			System.err.println("[error] Failed to start server");
			System.err.println("Tried to make a second instance");
			return;
		}
		clients.get("server").add(Integer.valueOf(ID.getPort()));
		if(addressList.get(selfID.getPort().toString()) == null)
			addressList.put(selfID.getPort().toString(), selfID.getIPStr());
		
		while(true) {
			try{
				log("listening for electable on " + ID.getIP() + " " + ID.getPort());
				server = new SaslSocketServer(new SpecificResponder(Electable.class, this),new InetSocketAddress(ID.getIP(),ID.getPort()));
				break;
			} catch(BindException e){
				log("address still in use - we will retry");
				stop();
				try { Thread.sleep(3000); } catch(InterruptedException e2) {}
			} catch(IOException e){
				System.err.println("[error] Failed to start server");
				e.printStackTrace(System.err);
				System.exit(1);
			}
		}
		server.start();
		pingingobject = new Pinger(this);
		pingingEveryone = new Thread(pingingobject);
		pingingEveryone.start();

		java.util.Date now = new java.util.Date();
		synctimertask = new SyncTimerTask();
		syncTimer.schedule(synctimertask, now , COUNTDOWN);
		thermostatCounter = 0;
		TimerCounter timerObj = new TimerCounter(this);
		timeKeeper = new Thread(timerObj);
		timeKeeper.start();
		
		try{
			server.join();
		} catch(InterruptedException e){}
	}
	
	private boolean find(int ID){
		for(String key: clients.keySet()){
			for(Integer entry: clients.get(key)){
				if (entry.intValue() == ID){
					return true;
				}
			}
		}
		return false;
	}
	
	private int getFreeID(){
		int result = 0;
		for(String key: clients.keySet()){
			for(Integer entry: clients.get(key)){
				if (entry.intValue() >= result){
					result = entry.intValue()+1;
				}
			}
		}	
		return result;
	}

	@Override
	public int ConnectLight(int LightID,CharSequence IP) throws AvroRemoteException{
		if (clients.get("lights") == null){
			Set<Integer> values = new HashSet<Integer>();
			clients.put("lights", values);
		}
		if( find(LightID) ){
			LightID = getFreeID();
		}
		NetAddress toAdd = new NetAddress(LightID,IP.toString());
		clients.get("lights").add(LightID);
		addressList.put(toAdd.getPort().toString(), toAdd.getIPStr());
		return LightID;
	}
	
	@Override
	public int ConnectThermostat(int SensorID,CharSequence IP) throws AvroRemoteException{
		if (clients.get("thermostat") == null){
			Set<Integer> values = new HashSet<Integer>();
			clients.put("thermostat", values);
		}
		if( find(SensorID) ){
			SensorID = getFreeID();
		}
		NetAddress toAdd = new NetAddress(SensorID,IP.toString());
		clients.get("thermostat").add(SensorID);
		addressList.put(toAdd.getPort().toString(), toAdd.getIPStr());
		return SensorID;
	}
	
	@Override
	public int ConnectFridge(int FridgeID,CharSequence IP) throws AvroRemoteException {
		if (clients.get("fridges") == null){
			Set<Integer> values = new HashSet<Integer>();
			clients.put("fridges", values);
		}
		if( find(FridgeID) ){
			FridgeID = getFreeID();
		}
		NetAddress toAdd = new NetAddress(FridgeID, IP.toString());
		
		clients.get("fridges").add(FridgeID);
		addressList.put(toAdd.getPort().toString(), toAdd.getIPStr());
		return FridgeID;
	}
	
	
	@Override
	public int ConnectUser(CharSequence username,CharSequence IP) throws AvroRemoteException {
		int ID = 0;
		if (users.containsValue(new SimpleEntry<CharSequence,Boolean>(username,false)) || users.containsValue(new SimpleEntry<CharSequence,Boolean>(username,true))){
			for (Integer it :users.keySet()){
				if(users.get(it).getKey() == username){
					users.get(it).setValue(true);
					ID = it;
				}
			}
		}
		if (ID == 0){
			ID = getFreeID();
			SimpleEntry<CharSequence,Boolean> tuple = new SimpleEntry<CharSequence,Boolean>(username,true);
			users.put(ID, tuple);
		}
		
		if(clients.get("users") == null){
			Set<Integer> values = new HashSet<Integer>();
			clients.put("users", values);
		}
		NetAddress toAdd = new NetAddress(ID,IP.toString());
		clients.get("users").add(ID);
		addressList.put(toAdd.getPort().toString(), toAdd.getIPStr());
		NotifyEnter(username);
		undoSavings();
		return ID;
	}
	
	@Override
	public boolean Switch(int lightID) throws AvroRemoteException {
		if (clients.get("lights")==null || !clients.get("lights").contains(lightID)){
			throw new AvroRemoteException("Exist");
		}
		
		NetAddress IP = new NetAddress(lightID,addressList.get(String.valueOf(lightID)).toString());
		if(IP.getIP() == null){
			throw new AvroRemoteException("IP PROBLEM");
		}
		
		try{
			Transceiver client = new SaslSocketTransceiver(new InetSocketAddress(IP.getIP(),IP.getPort()));
			Light proxy = (Light) SpecificRequestor.getClient(Light.class, client);
			proxy.LightSwitch();
			client.close();
		} catch(IOException e){
			throw new AvroRemoteException("Connect");
		}
		return true;
	}

	@Override
	public Map<CharSequence, List<Integer>> GetClients() throws AvroRemoteException {
		Map<CharSequence, List<Integer>> result = new HashMap<CharSequence, List<Integer>>();
		for(String key: clients.keySet()){
			List<Integer> ValueList = new Vector<Integer>();
			result.put(key, ValueList);
			for(Integer ID: clients.get(key)){
				try{
					NetAddress IP = new NetAddress(ID,addressList.get(String.valueOf(ID)).toString());
					if(IP.getIP() == null){
						continue;
					}
					Transceiver client = new SaslSocketTransceiver(new InetSocketAddress(IP.getIP(),IP.getPort()));
					switch (key) {
					case "server":
						DomServer proxyS = (DomServer) SpecificRequestor.getClient(DomServer.class, client);
						proxyS.IsAlive(OriginalServerIP,OriginalServerID);
						break;
					case "users":
						User proxyU = (User) SpecificRequestor.getClient(User.class, client);
						proxyU.IsAlive(this.selfID.getIPStr(), this.getID());
						break;
					case "lights":
						Light proxyL = (Light) SpecificRequestor.getClient(Light.class, client);
						proxyL.IsAlive();
						break;
					case "fridges":
						SmartFridge proxyF = (SmartFridge) SpecificRequestor.getClient(SmartFridge.class,client);
						proxyF.IsAlive(this.selfID.getIPStr(), this.getID());
						break;
					case "thermostat":
						Thermostat proxyT = (Thermostat) SpecificRequestor.getClient(Thermostat.class,client);
						proxyT.IsAlive(this.selfID.getIPStr(),this.getID());
						break;
					}
					client.close();
				}
				catch(IOException e){
					continue;
				}
				result.get(key).add(ID);
			}
		}
		return result;
	}
	
	@Override
	public Map<CharSequence, Boolean> GetServers() throws AvroRemoteException {
		Map<CharSequence, Boolean> result = new HashMap<CharSequence, Boolean>();
		for(Integer ID: clients.get("server")){
			boolean connected = true;
			NetAddress IP = new NetAddress(ID,addressList.get(ID.toString()).toString());
			if(IP.getIP() == null){
				continue;
			}
			try{
				Transceiver client = new SaslSocketTransceiver(new InetSocketAddress(IP.getIP(),IP.getPort()));
				DomServer proxy = (DomServer) SpecificRequestor.getClient(DomServer.class, client);
				proxy.IsAlive(OriginalServerIP,OriginalServerID);
				client.close();
			}
			catch(Exception e){
				connected = false;
			}
			result.put(ID.toString(), connected);
		}
		return result;
	}

	@Override
	public Map<CharSequence, Boolean> GetUsers() throws AvroRemoteException {
		Map<CharSequence, Boolean> result = new HashMap<CharSequence, Boolean>();
		for(SimpleEntry<CharSequence,Boolean> value: users.values()){
			result.put(value.getKey(), value.getValue());
			
		}
		return result;
	}

	@Override
	public Map<CharSequence, Boolean> GetLights() throws AvroRemoteException {
		Map<CharSequence, Boolean> result = new HashMap<CharSequence, Boolean>();
		if(clients.get("lights") == null){
			return result;
		}
		for(Integer ID: clients.get("lights")){
			boolean on;
			NetAddress IP = new NetAddress(ID,addressList.get(ID.toString()).toString());
			if(IP.getIP() == null){
				continue;
			}
			try{
				Transceiver client = new SaslSocketTransceiver(new InetSocketAddress(IP.getIP(),IP.getPort()));
				Light proxy = (Light) SpecificRequestor.getClient(Light.class, client);
				on = proxy.GetLightState();
				client.close();
			}
			catch(IOException e){
				continue;
			}
			result.put(ID.toString(), on);
		}
		return result;
	}
	//TODO finish
	@Override
	public Map<CharSequence, List<Double> > GetThermostats() throws AvroRemoteException {
		Map<CharSequence, List<Double> > result = new HashMap<CharSequence, List<Double> >();
		if(clients.get("thermostat") == null){
			return result;
		}
		for(Integer ID: clients.get("thermostat")){
			Double temperature;
			Double clock;
			NetAddress IP = new NetAddress(ID,addressList.get(ID.toString()).toString());
			if(IP.getIP() == null){
				continue;
			}
			try{
				Transceiver client = new SaslSocketTransceiver(new InetSocketAddress(IP.getIP(),IP.getPort()));
				Thermostat proxy = (Thermostat) SpecificRequestor.getClient(Thermostat.class, client);
				temperature = proxy.GetTemperature();
				clock = proxy.GetTime();
				client.close();
			}
			catch(IOException e){
				continue;
			}
			List<Double> timeAndClock = new ArrayList<Double>();
			timeAndClock.add(temperature);
			timeAndClock.add(clock);
			result.put(ID.toString(), timeAndClock);
		}
		return result;
	}

	@Override
	public Void LeaveHouse(int userID) throws AvroRemoteException {
		users.get(userID).setValue(false);
		
		
		boolean oneIn = false;
		for(SimpleEntry<CharSequence, Boolean> entry: users.values()){
			if (entry.getValue()){
				oneIn = true;
			}
		}
		
		if(!oneIn){
			startSaving();
		}
		NotifyLeave(users.get(userID).getKey());

		
		clients.get("users").remove(userID);
		addressList.remove(String.valueOf(userID));
		return null;
	}
	
	@Override
	public CharSequence ConnectUserToFridge(int userID, int fridgeID) throws AvroRemoteException {
		if(clients.get("fridges") == null){
			return "";
		}
		Integer fridge = null;
		for(Integer frID: clients.get("fridges")){
			if(frID == fridgeID){
				fridge = frID;
			}
		}
		
		if(fridge == null){
			return "";
		}
		boolean success = false;
		NetAddress IP = new NetAddress(fridge,addressList.get(fridge.toString()).toString());
		if(IP.getIP() == null){
			return "";
		}
		try{
			Transceiver client = new SaslSocketTransceiver(new InetSocketAddress(IP.getIP(),IP.getPort()));
			SmartFridge proxy = (SmartFridge) SpecificRequestor.getClient(SmartFridge.class, client);

			success = proxy.OpenFridge(userID,addressList.get(String.valueOf(userID)));
			//result = proxy.getContents();
			client.close();
		}
		catch(Exception e){
			log("UNCAUGHT EXCEPTION: " + e.getMessage());
		}
		if(success){
			return IP.getIPStr();
		}
		return "";
	}
	
	@Override
	public Map<CharSequence, List<CharSequence>> GetFridges() throws AvroRemoteException {
		Map<CharSequence, List<CharSequence>>  result = new HashMap<CharSequence, List<CharSequence> >() ;
		if(clients.get("fridges") == null){
			return result;
		}
		for(Integer ID: clients.get("fridges")){
			NetAddress IP = new NetAddress(ID,addressList.get(ID.toString()).toString());
			if(IP.getIP() == null){
				continue;
			}
			try{
				Transceiver client = new SaslSocketTransceiver(new InetSocketAddress(IP.getIP(),IP.getPort()));
				SmartFridge proxy = (SmartFridge) SpecificRequestor.getClient(SmartFridge.class, client);
				List<CharSequence> Contents = proxy.GetContents();
				client.close();
				result.put(ID.toString(), Contents);
			}
			catch(IOException e){
				continue;
			}
		}
		return result;
	}
	
	@Override
	public Void FridgeIsEmpty(int fridgeID) throws AvroRemoteException {
		for(Integer ID: clients.get("users")){
			NetAddress IP = new NetAddress(ID,addressList.get(ID.toString()).toString());
			if(IP.getIP() == null){
				continue;
			}
			try{
				Transceiver client = new SaslSocketTransceiver(new InetSocketAddress(IP.getIP(),IP.getPort()));
				User proxy = (User) SpecificRequestor.getClient(User.class, client);
				proxy.EmptyFridge(fridgeID);
				client.close();
			}
			catch(IOException e){
				continue;
			}
			
		}
		return null;
	}
	@Override
	public boolean IsAlive(CharSequence IPaddr, int ID) throws AvroRemoteException {
		ServerID = ID;
		ServerIP = IPaddr.toString();
		electiontimertask.cancel();
		electiontimertask = new ElectionTimerTask();
		deadservertimer.schedule(electiontimertask, COUNTDOWN);
		log("Test electable lists: clients " + clients +  " users: " + users );
		return true;
	}
	
	private void NotifyLeave(CharSequence username){
		for(Integer ID: clients.get("users")){
			if(users.get(ID).getKey()==username){
				continue;
			}
			NetAddress IP = new NetAddress(ID,addressList.get(ID.toString()).toString());
			if(IP.getIP() == null){
				continue;
			}
			try{
				Transceiver client = new SaslSocketTransceiver(new InetSocketAddress(IP.getIP(),IP.getPort()));
				User proxy = (User) SpecificRequestor.getClient(User.class, client);
				proxy.UserLeaves(username);
				client.close();
			}
			catch(IOException e){
				continue;
			}
			
		}
	}
	
	private void NotifyEnter(CharSequence username){
		for(Integer ID: clients.get("users")){
			NetAddress IP = new NetAddress(ID,addressList.get(ID.toString()).toString());
			if(IP.getIP() == null){
				continue;
			}
			try{
				Transceiver client = new SaslSocketTransceiver(new InetSocketAddress(IP.getIP(),IP.getPort()));
				User proxy = (User) SpecificRequestor.getClient(User.class, client);
				proxy.UserEnters(username);
				client.close();
			}
			catch(IOException e){
				continue;
			}
			
		}
	}
	
	private void startSaving(){
		log("start Saving");
		for(Integer light: clients.get("lights") ){
			NetAddress IP = new NetAddress(light,addressList.get(light.toString()).toString());
			if(IP.getIP() == null){
				continue;
			}
			try{
				Transceiver client = new SaslSocketTransceiver(new InetSocketAddress(IP.getIP(),IP.getPort()));
				Light proxy = (Light) SpecificRequestor.getClient(Light.class, client);
				if(proxy.GetLightState()){
					proxy.LightSwitch();
					savedLights.add(light);
				}
			} catch (Exception e){
				continue;
			}
		}
	}
	
	private void undoSavings() {
		List<Integer> toTurnOn = new Vector<Integer>(savedLights);
		savedLights.clear();
		for(Integer light: toTurnOn){
			NetAddress IP = new NetAddress(light,addressList.get(light.toString()).toString());
			if(IP.getIP() == null){
				continue;
			}
			try{
				Transceiver client = new SaslSocketTransceiver(new InetSocketAddress(IP.getIP(),IP.getPort()));
				Light proxy = (Light) SpecificRequestor.getClient(Light.class, client);
				proxy.LightSwitch();
			} catch (Exception e){
				continue;
			}
		}
		
	}
	
	/*
	 * When server dies, calls election for new server.
	 * Implements Roberts-Chang algorithm.
	 */
	public class ElectionTimerTask extends TimerTask{
		
		public void run(){
			log("Server dead");
			clients.remove("server");
			Election(0);
			
			standby(ELECTIONTIMEOUT);

		}
	}
	
	public void standby(long delay){
		electiontimertask.cancel();
		electiontimertask = new ElectionTimerTask();
		this.deadservertimer.schedule(this.electiontimertask, delay);
		log("Standby");
	}
	public void standDown(){
		this.electiontimertask.cancel();
		log("Standing down");
	}
	
	/*
	 * Pings original server to check when it comes back online.
	 */
	public void pingserver(){
		if(!this.getName().equalsIgnoreCase("server")){
			try{
				log("trying to ping server " + OriginalServerID);
				Transceiver client = new SaslSocketTransceiver(new InetSocketAddress(OriginalServerIP,OriginalServerID));
				Electable proxy = (Electable) SpecificRequestor.getClient(Electable.class, client);
				if(proxy.IsAlive(this.selfID.getIPStr(), this.getID())){
					log("server is back!!! syncing " + OriginalServerID);
					//resign being server, start being fridge or userclient
					proxy._sync(this.ConvertClients(true), this.ConvertUsers(true),this.addressList ,this.savedLights);
					this.stopserver();
				}
				client.close();
			} catch (Exception e){
				log("no server pingable");
			}
		}
			
	}
	@Override
	public Void UpdateTemperature(double temp){
		this.temperature = temp;
		if(this.temperatureHistory.size() >=3){
			this.temperatureHistory.remove(0);

		}
		this.temperatureHistory.add(temp);
		return null;
	}

	@Override
	public double GetTemperature() throws AvroRemoteException {
		boolean connected = false;
		
		for(Integer key: clients.get("thermostat")){
			NetAddress IP = new NetAddress(key,addressList.get(key.toString()).toString());
			try{
				Transceiver client = new SaslSocketTransceiver(new InetSocketAddress(IP.getIP(),IP.getPort()));
				Thermostat proxy = (Thermostat) SpecificRequestor.getClient(Thermostat.class, client);
				proxy.IsAlive(selfID.getIPStr(), selfID.getPort());
				client.close();
				connected = true;
			}
			catch(IOException e){
				continue;
			}
		}
		
		if(! connected){
			return 0;
		}
		
		return temperature;
	}

	@Override
	public List<Double> GetTemperatureHistory() throws AvroRemoteException {
		boolean connected = false;
		
		for(Integer key: clients.get("thermostat")){
			NetAddress IP = new NetAddress(key,addressList.get(key.toString()).toString());
			try{
				Transceiver client = new SaslSocketTransceiver(new InetSocketAddress(IP.getIP(),IP.getPort()));
				Thermostat proxy = (Thermostat) SpecificRequestor.getClient(Thermostat.class, client);
				proxy.IsAlive(selfID.getIPStr(), selfID.getPort());
				client.close();
				connected = true;
			}
			catch(IOException e){
				continue;
			}
		}
		
		if(! connected){
			return null;
		}
		
		return temperatureHistory;
	}
	
	//Berkeley algorithm for time synchronization
	public void CompareTime(){
		/*Parameters*/
		double epsilon = 2;
		double RTTmax = 0.8;
		/*code*/
		Vector< Double > counterlist =  new Vector<Double>();
		boolean connected = false;
		ClientCopy copy = this.Copy(); 
		
		//stage one, getting the data points of all clocks
		if(copy.clients.get("thermostat").size() != 0){
			for(Integer key: copy.clients.get("thermostat")){
				NetAddress IP = new NetAddress(key,copy.addressList.get(key.toString()).toString());
				try{
					Transceiver client = new SaslSocketTransceiver(new InetSocketAddress(IP.getIP(),IP.getPort()));
					Thermostat proxy = (Thermostat) SpecificRequestor.getClient(Thermostat.class, client);
					long millisec;
					long millisec2;
					Date _date = new Date();
					millisec = _date.getTime();
					double compareTime = proxy.GetTime();
					millisec2 = _date.getTime();
					long RTTmil = millisec2-millisec;
					double RTT = RTTmil/2;
					if(RTTmil/1000 > RTTmax)
						continue;
					compareTime += RTT;
					counterlist.add(new Double(compareTime));
					client.close();
					connected = true;
				}
				catch(IOException e){
					continue;
				}
			}
		}
		
		if(! connected){
			return;
		}
		//stage2 calculating average of those datapoints
		if(counterlist.size() != 0){
			double average = thermostatCounter;
			average = average/(counterlist.size()+1);
			double passedctr = 1; //amount of times the average was counted in.
			double divider = counterlist.size()+1;
			if(thermostatCounter == 0){
				average = 0;
				passedctr = 0;
				divider -= 1;
			}
			
			for(int i =0; i < counterlist.size(); i++){
				if(Math.abs(average*divider - counterlist.elementAt(i)) > epsilon && average != 0){
					continue;
				}
				passedctr ++;
				average += counterlist.elementAt(i)/divider;

			}
			if(passedctr != 0){
				average = average * (divider/passedctr);
			}
			//stage3 sending out corrections to clients
			connected = false;
			int indexctr = -1;
			if(copy.clients.get("thermostat").size() > 0){
				for(Integer key: copy.clients.get("thermostat")){
					indexctr += 1;
					NetAddress IP = new NetAddress(key,copy.addressList.get(key.toString()).toString());
					try{
						Transceiver client = new SaslSocketTransceiver(new InetSocketAddress(IP.getIP(),IP.getPort()));
						Thermostat proxy = (Thermostat) SpecificRequestor.getClient(Thermostat.class, client);
						double adjustment = average - counterlist.elementAt(indexctr);
						proxy.SetTime(adjustment);				
						client.close();
						connected = true;
					}
					catch(IOException e){
						continue;
					}
					
				}
			}
			thermostatCounter = average;
			return;
		}

		thermostatCounter = 0;
		

	}
	
	/*
	 * class that contains a deep copy of the clients and addresslist maps, to keep threads from overwriting it during functions. 
	 */
	public class ClientCopy{
		Map<String,Set<Integer> > clients;
		Map<CharSequence, CharSequence> addressList;
		
		/*ClientCopy(Map<String,Set<Integer> > _clients, Map<CharSequence, CharSequence> _addressList){
			clients = _clients;
			addressList = _addressList;
		}*/
		
	}
	ClientCopy Copy(){
		
		ClientCopy copy = new ClientCopy();
		copy.clients = new HashMap<String, Set<Integer> >();
		copy.addressList = new HashMap<CharSequence, CharSequence >();
		if(this.clients.get("thermostat").size() > 0){

			synchronized (clientsLock){

				for(String key: this.clients.keySet()){
					Set<Integer> ipList = new HashSet<Integer>();
					for(int ip: this.clients.get(key)){
						ipList.add(ip);
					}
				
					copy.clients.put(key, ipList);

				}
				for(CharSequence key: this.addressList.keySet()){

					copy.addressList.put(key, this.addressList.get(key));
				}
				
			}
			return copy;
		}

		copy.addressList = this.addressList;
		copy.clients = this.clients;

		return copy;
	}
	/*
	 * class that keeps the server's counter running so it can synchronize thermostat clocks.
	 */
	private class TimerCounter implements Runnable{
		ElectableClient owner;
		boolean stop = false;
		int timesCounter = 5;
		public TimerCounter(ElectableClient _owner){
			owner = _owner;
		}
		
		@Override
		public void run() {
			//System.out.println("RUNNING TIMER");

			while (! stop){
				try{
					
					for(int n = 0; n < timesCounter; n++){
						Thread.sleep(1000);
						if(thermostatCounter != 0)
								thermostatCounter += 1;
	
					}
					owner.CompareTime();
				}
				catch(InterruptedException e){}
				
			}
			
		}
	
	}
	
	void RemoveFromClients(String key, Integer ipaddr){
		synchronized (clientsLock){
			Set<Integer> RemoveTarget = this.clients.get(key);
			RemoveTarget.remove(ipaddr);
		}
	}
}