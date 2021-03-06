package domotics;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.text.DecimalFormat;
import java.util.Random;

import org.apache.avro.AvroRemoteException;
import org.apache.avro.ipc.SaslSocketTransceiver;
import org.apache.avro.ipc.Transceiver;
import org.apache.avro.ipc.specific.SpecificRequestor;

import domotics.SimpleClient;
import protocols.avro.Electable;
import protocols.avro.Thermostat;
import domotics.NetAddress;

	public class ThermostatClient extends SimpleClient implements Thermostat {
	public double temperature;
	private NetAddress sensorID;
	private Integer serverID;
	private String serverIP;
	private Thread pingingServer;
	private double counter;
	private double drift;
	private Random rand = new Random();
	
	public ThermostatClient(NetAddress ServerAddr, NetAddress MyAddr){
		this.sensorID = MyAddr;
		this.serverID = ServerAddr.getPort();
		this.serverIP = ServerAddr.getIPStr();
		this.pingingServer = new Thread(new ThermostatUpdater(this));
		this.counter = 0;
		this.drift = rand.nextDouble()/4; //min = 0; max = .25, to create a need to synchronize counters.
		
	}

	public void run(NetAddress serverAddress){
		NetAddress ID = this.getAddress();
		try{
			Transceiver client = new SaslSocketTransceiver(new InetSocketAddress(serverAddress.getIP(),serverAddress.getPort()));
			Electable proxy = (Electable) SpecificRequestor.getClient(Electable.class, client);
			ID.setPort(proxy.ConnectThermostat(ID.getPort(),ID.getIPStr()));
			client.close();
		} catch(IOException e){
			System.err.println("Error connecting to server");
			exceptionLog(e);
			System.exit(1);
		}

		System.out.println("You have ID: "+Integer.toString(this.getAddress().getPort()));
		serverRunning = new Thread(new ServerThread(this.getAddress(),this));
		serverRunning.start();
		
	}
	
	public class ThermostatUpdater implements Runnable{
		ThermostatClient ptr;
		boolean stop  = false;
		long sleeper = 3000;
		long countertime = 1000;
		long timesCounter = sleeper/countertime;
		
		//pings server to update its temperature and clocks
		public ThermostatUpdater(ThermostatClient owner){
			ptr = owner;
			log("hello");
			ptr.temperature = ((rand.nextDouble() + 0.5) * 10) + 10;
		}
		
		public void run(){
			while(! stop){
				try{		
					for(int n = 0; n < timesCounter; n++){
						Thread.sleep(countertime);
						counter += 1 + drift;
					}	
				}	
				catch(InterruptedException e){}
				catch(Exception e){
					exceptionLog(e);
				}
				log("update: " + ptr.serverIP + ":" + ptr.serverID);
				if(rand.nextDouble()> 0.5){
					temperature = temperature + (rand.nextDouble());
				}
				else{
					temperature = temperature - (rand.nextDouble());
				}
				DecimalFormat df = new DecimalFormat("#.##");
				System.out.println(df.format(temperature));
				log("COUNTER : " + counter);
				System.out.println(df.format(counter));
				log("temperature: " + temperature);
			}
		}
		
	}
	
	@Override
	public double GetTemperature() throws AvroRemoteException{
		return temperature;
	}
	

	

	
	public static void main(String[] args){
		clientinfo info = mainstart("thermostat",args);
		
		ThermostatClient ThisSensor = new ThermostatClient(info.serverAddr, info.MyAddr);
		/*ThisSensor.SensorID = info.MyAddr;
		ThisSensor.serverID= info.serverAddr.getPort();
		ThisSensor.serverIP = info.serverAddr.getIPStr();*/
		ThisSensor.run(info.serverAddr);
		ThisSensor.pingingServer.start();
		while(true){
			int input = 0;
			try{
				input = System.in.read();
			} catch(Exception e){
				
			}
			if (input =='e'){ 
				ThisSensor.stop();
				break;
			}
		}
		
	}



	@Override
	public int getID() {
		return sensorID.getPort();
	}

	@Override
	public String getName() {
		return "thermostat";
	}

	@Override
	public NetAddress getAddress() {
		return sensorID;
	}

	@Override
	public boolean IsAlive(CharSequence IPaddr, int ID) throws AvroRemoteException {
		serverID = ID;
		serverIP = IPaddr.toString();
		//log("newID: " + ID + ":" + IPaddr.toString());
		return true;
	}

	@Override
	public Class getClientClass() {
		return Thermostat.class;
	}
	
	@Override
	public Void SetTime(double Time){
		counter += Time; 
		return null;
	}
	@Override
	public double GetTime(){
		
		return counter;
	}
}