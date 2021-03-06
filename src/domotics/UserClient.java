package domotics;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.avro.ipc.SaslSocketServer;
import org.apache.avro.ipc.SaslSocketTransceiver;
import org.apache.avro.ipc.Server;
import org.apache.avro.ipc.Transceiver;
import org.apache.avro.ipc.specific.SpecificRequestor;
import org.apache.avro.ipc.specific.SpecificResponder;

import domotics.ElectableClient;
import protocols.avro.Electable;
import protocols.avro.SmartFridge;
import protocols.avro.User;
//import NetAddress;

import org.apache.avro.AvroRemoteException;
import asg.cliche.Command;
import asg.cliche.ShellFactory;


public class UserClient extends ElectableClient implements User{
	private Server server = null;
	private NetAddress serverAddress = null;
	private String name = "Foo";
	private NetAddress openFridgeID = null;
	private Thread serverThread = null;
	private RunServer serverRun = null;
	private boolean fridgeOpen = false;

	
	UserClient(NetAddress server, String name, NetAddress myAddr){
		this.serverAddress = server;
		this.name = name;
		this.selfID = myAddr;
	}


	public int getID(){
		return selfID.getPort();
	}
	public String getName(){
		return "UserClient";
	}
	
	public void start(){
		serverRun= new RunServer(this);
		serverThread = new Thread(serverRun);
		serverThread.start();
	}

	
	public void stop(){
		log("stop");
		server.close();
	}
	
	public class RunServer implements Runnable{
		UserClient ptr = null;
		public RunServer(UserClient above){
			ptr= above;
		}
		public void run(){
			try{
				log("listening");
				server = new SaslSocketServer(new SpecificResponder(User.class, ptr),new InetSocketAddress(selfID.getIP(),selfID.getPort()));
			} catch(IOException e){
				System.err.println("[error] Failed to start server");
				exceptionLog(e);
				System.exit(1);
			}
			server.start();
			try{
				server.join();
			} catch(InterruptedException e){}
			
		}
		
		public void stop(){
			server.close();
		}
	}
	
	@Command
	public void EnterHouse(){
		if(this.fridgeOpen){
			System.out.println("You must close the fridge first!");
			return;
		}
		if(serverRun != null){
			System.out.println("Already connected");
			return;
		}
		try{
			Transceiver client = new SaslSocketTransceiver(new InetSocketAddress(ServerIP,ServerID));
			Electable proxy = (Electable) SpecificRequestor.getClient(Electable.class, client);
			selfID.setPort(proxy.ConnectUser(name,selfID.getIPStr()));
			client.close();
		} catch(Exception e){
			System.out.println("Could not connect to the server");
			System.err.println("Error connecting to server");
			exceptionLog(e);
			return;
		}

		this.start();
		try{
			this.standby(COUNTDOWN);
		} catch(Exception e){
			
		}
	}
	
	@Command
	public void LeaveHouse(){
		if(this.fridgeOpen){
			System.out.println("You must close the fridge first!");
			return;
		}
		if(server == null){
			System.out.println("You are not connected");
			return;
		}
		try{
			Transceiver client = new SaslSocketTransceiver(new InetSocketAddress(ServerIP,ServerID));
			Electable proxy = (Electable) SpecificRequestor.getClient(Electable.class, client);
			proxy.LeaveHouse(selfID.getPort());
			client.close();
		} catch (IOException e){
			//Cannot connect to server so things should happen
		} catch (Exception e){
			System.out.println(e.getMessage());
		}
		serverRun.stop();
		serverThread.interrupt();
		server.close();
		serverRun = null;
		serverThread = null;
		server = null;
		this.standDown();
		
	}

	
	@Command
	public String SwitchLight(int ID){
		if(this.fridgeOpen){
			System.out.println("You must close the fridge first!");
			return null;
		}
		if (server == null){
			return "You are not connected";
		}
		try{
			Transceiver client = new SaslSocketTransceiver(new InetSocketAddress(ServerIP,ServerID));
			Electable proxy = (Electable) SpecificRequestor.getClient(Electable.class, client);
			proxy.Switch(ID);
			client.close();
		} catch(AvroRemoteException e){
			if(e.getValue() == "Exist"){
				System.out.println("[error]: This light does not exist");
			}
			if(e.getValue() == "Connect"){
				System.out.println("[error]: Could not connect to that light");
			}
			System.out.println("Something");
			System.err.println(e.getMessage());
			
		} catch(IOException e){
			System.err.println("Error connecting to server");
		} catch(Exception e){
			return "Problem connecting to that light.";
		}
		return null;		
	}
	
	@Command
	public String getClients(){
		if(this.fridgeOpen){
			System.out.println("You must close the fridge first!");
			return null;
		}
		
		if (server == null){
			return "You are not connected";
		}
		String result = "";
		result += getServers();
		result += getUsers();
		result += getLights();
		result += getFridges();
		result += getThermostats();
		
		return result;
	}
	

	@Command
	public String getThermostats(){
		if(this.fridgeOpen){
			System.out.println("You must close the fridge first!");
			return null;
		}
		if (server == null){
			return "You are not connected";
		}
		Map<CharSequence, List<Double> > thermostats = new HashMap<CharSequence,List<Double> >();
		try{
			Transceiver client = new SaslSocketTransceiver(new InetSocketAddress(ServerIP,ServerID));
			Electable proxy = (Electable) SpecificRequestor.getClient(Electable.class, client);
			thermostats = proxy.GetThermostats();
			client.close();
		} catch(IOException e){
			System.err.println("Error connecting to server");
			exceptionLog(e);
			return "error connecting to server";
		}
		String result = "";
		DecimalFormat df = new DecimalFormat("#.##");
		for (CharSequence Key: thermostats.keySet()){
			result  +="Thermostat"+'\t'+ Key.toString() + '\t';
			String temp = df.format(thermostats.get(Key).get(0));
			String clock = df.format(thermostats.get(Key).get(1));
			result += "temperature: " + temp + ", time: " + clock + "\n";
		}
		return result;		
	}
	
	@Command
	public String getServers(){
		if(this.fridgeOpen){
			System.out.println("You must close the fridge first!");
			return null;
		}
		if (server == null){
			return "You are not connected";
		}
		log("SERVERID: " + serverAddress.getPort());
		String result = "";
		Map<CharSequence, Boolean> Servers = new HashMap<CharSequence,Boolean>();
		try{
			Transceiver client = new SaslSocketTransceiver(new InetSocketAddress(ServerIP,ServerID));
			Electable proxy = (Electable) SpecificRequestor.getClient(Electable.class, client);
			Servers = proxy.GetServers();
			client.close();
		} catch(IOException e){
			System.out.println("Could not connect to the server");
			System.err.println("Error connecting to server");
			exceptionLog(e);
		}
		for(CharSequence ID: Servers.keySet()){
			result +="Server"+'\t'+  ID.toString()+'\t';
			if (Servers.get(ID)){
				result += "CONNECTED";
			}
			else{
				result += "DISCONNECTED";
			}
			result += '\n';
		}
		return result;
	}
	
	@Command
	public String getUsers(){
		if(this.fridgeOpen){
			System.out.println("You must close the fridge first!");
			return null;
		}
		if (server == null){
			return "You are not connected";
		}
		String result = "";
		Map<CharSequence, Boolean> Users = new HashMap<CharSequence,Boolean>();
		try{
			Transceiver client = new SaslSocketTransceiver(new InetSocketAddress(ServerIP,ServerID));
			Electable proxy = (Electable) SpecificRequestor.getClient(Electable.class, client);
			Users = proxy.GetUsers();
			client.close();
		} catch(IOException e){
			System.err.println("Error connecting to server");
			exceptionLog(e);
			return "Could not connect to the server";
		}
		for(CharSequence ID: Users.keySet()){
			result +="User"+'\t'+  ID.toString()+'\t';
			if (Users.get(ID)){
				result += "INSIDE";
			}
			else{
				result += "OUTSIDE";
			}
			result += '\n';
		}
		return result;
	}
	
	@Command
	public String getLights(){
		if(this.fridgeOpen){
			System.out.println("You must close the fridge first!");
			return null;
		}
		if (server == null){
			return "You are not connected";
		}
		Map<CharSequence, Boolean> lights = new HashMap<CharSequence,Boolean>();
		try{
			Transceiver client = new SaslSocketTransceiver(new InetSocketAddress(ServerIP,ServerID));
			Electable proxy = (Electable) SpecificRequestor.getClient(Electable.class, client);
			lights = proxy.GetLights();
			client.close();
		} catch(IOException e){
			System.err.println("Error connecting to server");
			exceptionLog(e);
			return "Error connecting to server";
		}
		String result = "";
		for (CharSequence Key: lights.keySet()){
			result  +="Light"+'\t'+ Key.toString() + '\t';
			if (lights.get(Key)){
				result += "ON\n";
			}
			else{
				result += "OFF\n";
			}
		}
		return result;
	}
	
	@Command
	public String hello(){
		if(this.fridgeOpen){
			System.out.println("You must close the fridge first!");
			return null;
		}
		return "Hello World";
	}
	
	@Command
	public String getTemperature(){
		if(this.fridgeOpen){
			System.out.println("You must close the fridge first!");
			return null;
		}
		if (server == null){
			return "You are not connected";
		}
		String result = "It is ";
		Double Temperature = 0.0;
		try{
			Transceiver client = new SaslSocketTransceiver(new InetSocketAddress(ServerIP,ServerID));
			Electable proxy = (Electable) SpecificRequestor.getClient(Electable.class, client);
			Temperature = proxy.GetTemperature();
			client.close();
		} catch(IOException e){
			System.err.println("Error connecting to server");
			exceptionLog(e);
			return "Could not connect to the server";
		}
		if (Temperature == 0.0){
			return "No sensor connected";
		}
		DecimalFormat df = new DecimalFormat("#.##");
		result+=df.format(Temperature);
		result += " degrees.";
		return result;
	}
	
	@Command
	public String getTemperatureHistory(){
		if(this.fridgeOpen){
			System.out.println("You must close the fridge first!");
			return null;
		}
		if (server == null){
			return "You are not connected";
		}
		List<Double> Temperature = null;
		try{
			Transceiver client = new SaslSocketTransceiver(new InetSocketAddress(ServerIP,ServerID));
			Electable proxy = (Electable) SpecificRequestor.getClient(Electable.class, client);
			Temperature = proxy.GetTemperatureHistory();
			client.close();
		} catch(IOException e){
			System.err.println("Error connecting to server");
			exceptionLog(e);
			return "Could not connect to the server";
		}
		if (Temperature == null){
			return "No sensor connected";
		}
		String result = "";
		DecimalFormat df = new DecimalFormat("#.##");
		for (Double temp: Temperature){
			result += (Temperature.indexOf(temp)+1);
			result += ". ";
			result += df.format(temp);
			result += " dergrees.";
			result += '\n';
		}
		return result;
	}
	
	@Command
	public String getFridges(){
		if(this.fridgeOpen){
			System.out.println("You must close the fridge first!");
			return null;
		}
		if (server == null){
			System.out.println("You are not connected");
			return null;
		}
		log("Getting fridges from " + serverAddress.getIPStr() + " " + serverAddress.getPort());
		String result = "";
		Map<CharSequence, List<CharSequence>> fridges = new HashMap<CharSequence, List<CharSequence>>();// = new List<Integer>();
		try{
			Transceiver client = new SaslSocketTransceiver(new InetSocketAddress(ServerIP,ServerID));
			Electable proxy = (Electable) SpecificRequestor.getClient(Electable.class, client);
			fridges = proxy.GetFridges();
			client.close();
		} catch(IOException e){
			System.err.println("Error connecting to server");
			exceptionLog(e);
			return "Error connecting to server";
		}
		
		for (CharSequence ID: fridges.keySet()){
			result += "Fridge"+'\t'+ID+'\t';
			if(fridges.get(ID) == null || fridges.get(ID).isEmpty()){
				result += "EMTPY";
			}
			else{
				result += fridges.get(ID).toString();
			}
			result += '\n';
		}
		return result;
	}
	
	@Command
	public Void openFridge(int fridgeID){
		if(this.fridgeOpen){
			System.out.println("You must close the fridge first!");
			return null;
		}
		if (server == null){
			System.out.println("You are not connected");
			return null;
		}
		this.fridgeOpen = true;
		//List<Integer> fridges = new Vector<Integer>();// = new List<Integer>();
		CharSequence success = "";
		try{
			Transceiver client = new SaslSocketTransceiver(new InetSocketAddress(ServerIP,ServerID));
			Electable proxy = (Electable) SpecificRequestor.getClient(Electable.class, client);
			success = proxy.ConnectUserToFridge(selfID.getPort(), fridgeID);
			client.close();
		} catch(IOException e){
			System.err.println("Error connecting to Fridge");
			exceptionLog(e);
			this.fridgeOpen = false;
		}

		this.openFridgeID = new NetAddress(fridgeID,String.valueOf(success));
		return null;
	}
	
	@Command
	public Void AddItemToFridge(String item){
		if (server == null){
			System.out.println("You are not connected");
			return null;
		}
		//List<Integer> fridges = new Vector<Integer>();// = new List<Integer>();
		if(this.openFridgeID != null){
			try{
				Transceiver client = new SaslSocketTransceiver(new InetSocketAddress(this.openFridgeID.getIP(),openFridgeID.getPort()));
				SmartFridge proxy = (SmartFridge) SpecificRequestor.getClient(SmartFridge.class, client);
				proxy.AddItem(selfID.getPort(), item);
				client.close();
			} catch(IOException e){
				System.err.println("Error connecting to Fridge");
				exceptionLog(e);
				this.fridgeOpen = false;
			}
			 catch(Exception e){
				 log("UNCAUGHT EXCEPTION : " + e.getMessage());
			 }
		}
		return null;
	}
	
	@Command
	public Void RemoveItemFromFridge(String item){
		if (server == null){
			System.out.println("You are not connected");
			return null;
		}
		if(this.openFridgeID != null){
			try{
				Transceiver client = new SaslSocketTransceiver(new InetSocketAddress(this.openFridgeID.getIP(),openFridgeID.getPort()));
				SmartFridge proxy = (SmartFridge) SpecificRequestor.getClient(SmartFridge.class, client);
				proxy.RemoveItem(selfID.getPort(), item);
				client.close();
			} catch(IOException e){
				System.err.println("Error connecting to Fridge");
				exceptionLog(e);
				this.fridgeOpen = false;
			}
		}
		return null;
	}
	
	@Command
	public Void CloseFridge(){
		if (server == null){
			System.out.println("You are not connected");
			return null;
		}
		if(this.openFridgeID != null){
			try{
				Transceiver client = new SaslSocketTransceiver(new InetSocketAddress(this.openFridgeID.getIP(),openFridgeID.getPort()));
				SmartFridge proxy = (SmartFridge) SpecificRequestor.getClient(SmartFridge.class, client);
				proxy.CloseFridge(selfID.getPort());
				client.close();
				this.openFridgeID = null;
			} catch(IOException e){
				System.err.println("Error connecting to Fridge");
				exceptionLog(e);
				this.fridgeOpen = false;
			}
		}
		this.fridgeOpen = false;
		return null;
	}
	//Command strictly for testing purposes, to enforce wait times in scripts so that the client can keep up with commands
	@Command
	public Void WAIT(){
		log("Waiting");
		try{
			Thread.sleep(2000);
		}
		catch(InterruptedException e){
			
		}
		catch(Exception e){
			exceptionLog(e);
		}
		return null;
	}
	
	public static void main(String[] args){
		System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "warn");
		Integer ServerID = 6789;
		String ServerIP = "127.0.0.1";
		String Name = "Bob";
		String UserIP = "127.0.0.1";

		if (args.length>0){
			ServerID = Integer.valueOf(args[0]);
		}
		if (args.length>1){
			ServerIP = args[1];
		}
		if (args.length>2){
			Name = args[2];
		}
		if(args.length>3){
			UserIP = args[3];
		}
		
		NetAddress ServerAddr = new NetAddress(ServerID,ServerIP);
		NetAddress UserAddr = new NetAddress(0,UserIP);
		
		if(ServerAddr.getIP() == null){
			System.out.println("Invalid Server IP");
			System.exit(-1);
		}
		if(UserAddr.getIP() == null){
			System.out.println("Invalid User IP");
			System.exit(-1);
		}
		
		UserClient myUser = new UserClient(ServerAddr,Name,UserAddr);

		
		try{
			myUser.EnterHouse();
			ShellFactory.createConsoleShell(myUser.name, "Domotics User", myUser).commandLoop();
		} catch(IOException e){
			System.exit(1);
		}
		catch(Exception e){}
		}

	@Override
	public Void UserEnters(CharSequence username) throws AvroRemoteException {
		System.out.println(username+" has entered the building.");
		System.out.print(name+"> ");
		return null;
	}

	@Override
	public Void UserLeaves(CharSequence username) throws AvroRemoteException {
		if (username == name){
			return null;
		}
		System.out.println(username+" has left the building.");
		System.out.print(name+"> ");
		return null;
	}

	@Override
	public Void EmptyFridge(int fridgeID) throws AvroRemoteException {
		System.out.println("Fridge "+String.valueOf(fridgeID)+" is empty.");
		System.out.print(name+"> ");
		return null;
	}
	
	
}