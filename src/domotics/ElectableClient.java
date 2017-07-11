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
import java.util.AbstractMap.SimpleEntry;

import protocols.avro.*;
import domotics.NetAddress;

public abstract class ElectableClient extends Client implements Electable, Runnable {
	private Map<String,Set<Integer> > clients = new ConcurrentHashMap<String,Set<Integer> >();
	private Map<Integer,SimpleEntry<CharSequence,Boolean>> users = new ConcurrentHashMap<Integer,SimpleEntry<CharSequence,Boolean>>();
	private Map<CharSequence, CharSequence> addressList = new ConcurrentHashMap<CharSequence,CharSequence>();
	private List<Integer> SavedLights = new Vector<Integer>();
	private int UPPERBOUND = 64*1024 + 1;
	protected NetAddress SelfID = null;
	private pinger pingingobject = null;
	private Thread PingingEveryone = null;
	HashMap<Integer, pinginfo> PingMap = new HashMap<Integer, pinginfo>();
	private Timer syncTimer = new Timer();
	private SyncTimerTask synctimertask = new SyncTimerTask();
	private final long countdown = 10000;
	private Thread ServerThread = null;
	private Timer deadservertimer = new Timer();
	private ElectionTimerTask electiontimertask = new ElectionTimerTask();
	public Integer ServerID = 6789;
	public String ServerIP = "127.0.0.1";
	public Integer OriginalServerID = 6789;
	public String OriginalServerIP = "127.0.0.1";
	private double temperature = 0;
	protected Server server = null;
	private Object ElectionLock = new Object(); 
	private boolean ElectionBusy = false;
	public List<Double> temperatureHistory = new ArrayList<Double>();

	public void stopserver(){
		System.out.println("This is not a server anymore");
		log("cancelling pinger");
		pingingobject.stop();
		log("stopping server");
		server.close();
		log("cancelling synchronizer");
		synctimertask.cancel();
		this.start();
		this.standby();
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
			ServerThread = new Thread(this);
			ServerThread.start();
		}
		else{
			log(": stop server thread");
		}
	}
	
	public Void _sync(Map<CharSequence,List<Integer> > _clients,	Map<CharSequence,Map<CharSequence,Boolean>> _users, Map<CharSequence, CharSequence> _addresses,List<Integer> _lights){
		log("in sync; clients: "+ _clients + " users: " + _users+" addresses:"+_addresses+" lights:"+_lights);
		HashMap<CharSequence,List<Integer>> clientlist = new HashMap<>(_clients);
		HashMap<CharSequence,Map<CharSequence,Boolean>> userlist = new HashMap<>(_users);
		
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
		addressList.putAll(_addresses);
		SavedLights = new Vector<Integer>(_lights);
		return null;
	}
	@Override
	public boolean election(int LastID){
		log("ENTER ELECTION");
		try {
	
		int OwnID = this.getID();

		int nextInChain = UPPERBOUND;
		
		int min = UPPERBOUND;
		for(String key: clients.keySet()){
			for(Integer ID: clients.get(key)){
				if(key.equalsIgnoreCase("fridges") || key.equalsIgnoreCase("users")){
					if(ID < nextInChain && ID > OwnID){
						nextInChain = ID;
						
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
			elected(OwnID, nextInChain);
			return true;
		}

		synchronized(ElectionLock) {
			if (ElectionBusy)
				return false;
			ElectionBusy = true; // set to false in finally
		}
		
		try {
			log("election: LastID: " + OwnID + " NextInChain: " + nextInChain );
			
			Transceiver client = null;
			try{
				CharSequence tempchain = Integer.toString(nextInChain);
				log("addresslist: " + addressList.size() );
				NetAddress IP = new NetAddress(nextInChain, (String)addressList.get( tempchain));
				client = new SaslSocketTransceiver(new InetSocketAddress(IP.getIP(),IP.getPort()));
				Electable.Callback proxy = (Electable.Callback) SpecificRequestor.getClient(Electable.Callback.class, client);
				if(OwnID > LastID){
					log("sending election "+OwnID+" to Ip: " + IP.getIP() + "," + IP.getPort());
					proxy.election(OwnID);
				}
				else{
					log("sending election "+LastID+" to Ip: " + IP.getIP() + "," + IP.getPort());
					proxy.election(LastID);
				}
	
			}
			catch(AvroRemoteException e){
					log("interrupted election");
					log("unexpected remote exception " + e);
			}
			catch(IOException e){
				log("exception during election: " + e);
			}
			finally{
				try {
					if(client != null)
						client.close();
				}
				catch (IOException e){}
			}
		} finally {
			ElectionBusy = false;
		}
		
		} finally{
			log("LEAVE ELECTION");
		}
		return true;
	}
	
	@Override
	public boolean elected(int _ElectedID, int nextInChain){
		log("ENTER ELECTED");
		try {
			
		log("elected notification: LastID: " + _ElectedID + " NextInChain: " + nextInChain );
		
		if(nextInChain == this.getID()) {
			log("elected notification reached end of chain " + nextInChain);
			return false;
		}
		
		
		Transceiver client = null;
		try{
			NetAddress IP = new NetAddress(nextInChain,(String)(addressList.get(Integer.toString(nextInChain))));
			client = new SaslSocketTransceiver(new InetSocketAddress(IP.getIP(),IP.getPort()));
			Electable.Callback proxy = (Electable.Callback) SpecificRequestor.getClient(Electable.Callback.class, client);
			proxy.elected(_ElectedID, nextInChain);
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
	//_________________________________________________DomoticsServer inherit methods_________________________________________________________\\
	public class pinginfo{
		public int missedpings = 0;
	}
	
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
			
			for(String key: clients.keySet()){
				for(Integer ID: clients.get(key)){
					Transceiver client = null;
					try{
						// NetAddress IP = new NetAddress(ID,String.valueOf(addressList.get(ID.toString())));
						NetAddress IP = new NetAddress(ID,(String)(addressList.get(ID.toString())));
						if(IP.getIP() == null){
							continue;
						}
						switch (key) {
						case "users":
							log("syncing " + key + " " + ID);
							client = new SaslSocketTransceiver(new InetSocketAddress(IP.getIP(),IP.getPort()));
							User proxyU = (User) SpecificRequestor.getClient(User.class, client);
							proxyU._sync(clientlist, userlist,addressList,SavedLights);
							break;
						case "fridges":
							log("syncing " + key + " " + ID);
							client = new SaslSocketTransceiver(new InetSocketAddress(IP.getIP(),IP.getPort()));
							SmartFridge proxyF = (SmartFridge) SpecificRequestor.getClient(SmartFridge.class,client);
							proxyF._sync(clientlist, userlist,addressList,SavedLights);
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
	public class pinger implements Runnable{
		ElectableClient ptr;
		int sleeper= 3000;
		public boolean _run = true;
		int threshold = 3;
		
		public pinger(ElectableClient electable){
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
						if(PingMap.get(ID) == null){
							pinginfo newinfo = new pinginfo();
							PingMap.put(ID, newinfo);
						}
						Transceiver client = null;
						try{
							NetAddress IP = new NetAddress(ID,(String)(addressList.get(ID.toString())));
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
								runningsmooth = proxyU.IsAlive(ptr.SelfID.getIPStr(), ptr.getID());
								break;
							case "lights":
								Light proxyL = (Light) SpecificRequestor.getClient(Light.class, client);
								runningsmooth = proxyL.IsAlive();
								break;
							case "fridges":
								SmartFridge proxyF = (SmartFridge) SpecificRequestor.getClient(SmartFridge.class,client);
								runningsmooth = proxyF.IsAlive(ptr.SelfID.getIPStr(), ptr.getID());
								break;
							case "thermostat":
								Thermostat proxyT = (Thermostat) SpecificRequestor.getClient(Thermostat.class,client);
								runningsmooth = proxyT.IsAlive(ptr.SelfID.getIPStr(), ptr.getID());
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
							pinginfo missping = PingMap.get(ID);
							missping.missedpings ++;
							if(missping.missedpings >= threshold){
								PingMap.remove(ID);
								clients.get(key).remove(ID);
								SavedLights.remove(ID);
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
	public void run(){		
		NetAddress ID = SelfID;
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
		addressList.put(SelfID.getPort().toString(), SelfID.getIPStr());
		
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
		pingingobject = new pinger(this);
		PingingEveryone = new Thread(pingingobject);
		PingingEveryone.start();
		java.util.Date now = new java.util.Date();
		synctimertask = new SyncTimerTask();
		syncTimer.schedule(synctimertask, now , countdown);
		
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
		
		NetAddress IP = new NetAddress(lightID,(String)addressList.get(String.valueOf(lightID)));
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
					NetAddress IP = new NetAddress(ID,(String)addressList.get(String.valueOf(ID)));
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
						proxyU.IsAlive(this.SelfID.getIPStr(), this.getID());
						break;
					case "lights":
						Light proxyL = (Light) SpecificRequestor.getClient(Light.class, client);
						proxyL.IsAlive();
						break;
					case "fridges":
						SmartFridge proxyF = (SmartFridge) SpecificRequestor.getClient(SmartFridge.class,client);
						proxyF.IsAlive(this.SelfID.getIPStr(), this.getID());
						break;
					case "thermostat":
						Thermostat proxyT = (Thermostat) SpecificRequestor.getClient(Thermostat.class,client);
						proxyT.IsAlive(this.SelfID.getIPStr(),this.getID());
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
			NetAddress IP = new NetAddress(ID,(String)addressList.get(ID.toString()));
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
			NetAddress IP = new NetAddress(ID,(String)addressList.get(ID.toString()));
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
		NetAddress IP = new NetAddress(fridge,(String)addressList.get(fridge.toString()));
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
			NetAddress IP = new NetAddress(ID,(String)(addressList.get(ID.toString())));
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
			NetAddress IP = new NetAddress(ID,(String)addressList.get(ID.toString()));
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
		deadservertimer.schedule(electiontimertask, countdown);
		log("Test electable lists: clients " + clients +  " users: " + users );
		return true;
	}
	
	private void NotifyLeave(CharSequence username){
		for(Integer ID: clients.get("users")){
			if(users.get(ID).getKey()==username){
				continue;
			}
			NetAddress IP = new NetAddress(ID,(String)addressList.get(ID.toString()));
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
			NetAddress IP = new NetAddress(ID,(String)addressList.get(ID.toString()));
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
			NetAddress IP = new NetAddress(light,(String)addressList.get(light.toString()));
			if(IP.getIP() == null){
				continue;
			}
			try{
				Transceiver client = new SaslSocketTransceiver(new InetSocketAddress(IP.getIP(),IP.getPort()));
				Light proxy = (Light) SpecificRequestor.getClient(Light.class, client);
				if(proxy.GetLightState()){
					proxy.LightSwitch();
					SavedLights.add(light);
				}
			} catch (Exception e){
				continue;
			}
		}
	}
	
	private void undoSavings() {
		List<Integer> toTurnOn = new Vector<Integer>(SavedLights);
		SavedLights.clear();
		for(Integer light: toTurnOn){
			NetAddress IP = new NetAddress(light,(String)addressList.get(light.toString()));
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
	
	//__________________________________________________________SmartFridge inherit_______________________________________________________________\\
	public class ElectionTimerTask extends TimerTask{

		public void run(){
			//robert chang yey
			log("Server dead");
			clients.remove("server");
			election(0);

		}
	}
	
	public void standby(){
		this.deadservertimer.schedule(this.electiontimertask, this.countdown);
		log("Standby");
	}
	public void standDown(){
		this.electiontimertask.cancel();
		log("Standing down");
	}
	public void pingserver(){
		if(!this.getName().equalsIgnoreCase("server")){
			try{
				log("trying to ping server " + OriginalServerID);
				Transceiver client = new SaslSocketTransceiver(new InetSocketAddress(OriginalServerIP,OriginalServerID));
				Electable proxy = (Electable) SpecificRequestor.getClient(Electable.class, client);
				if(proxy.IsAlive(this.SelfID.getIPStr(), this.getID())){
					log("server is back!!! syncing " + OriginalServerID);
					//resign being server, start being fridge or userclient
					proxy._sync(this.ConvertClients(true), this.ConvertUsers(true),this.addressList ,this.SavedLights);
					this.stopserver();
				}
				client.close();
			} catch (Exception e){
				log("no server pingable");
			}
		}
			
	}
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
			NetAddress IP = new NetAddress(key,(String)addressList.get(key.toString()));
			try{
				Transceiver client = new SaslSocketTransceiver(new InetSocketAddress(IP.getIP(),IP.getPort()));
				Thermostat proxy = (Thermostat) SpecificRequestor.getClient(Thermostat.class, client);
				proxy.IsAlive(SelfID.getIPStr(), SelfID.getPort());
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
			NetAddress IP = new NetAddress(key,(String)addressList.get(key.toString()));
			try{
				Transceiver client = new SaslSocketTransceiver(new InetSocketAddress(IP.getIP(),IP.getPort()));
				Thermostat proxy = (Thermostat) SpecificRequestor.getClient(Thermostat.class, client);
				proxy.IsAlive(SelfID.getIPStr(), SelfID.getPort());
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

}