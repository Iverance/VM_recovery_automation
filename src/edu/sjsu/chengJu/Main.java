package edu.sjsu.chengJu;

import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import com.vmware.vim25.HostConnectSpec;
import com.vmware.vim25.ManagedEntityStatus;
import com.vmware.vim25.mo.Folder;
import com.vmware.vim25.mo.HostSystem;
import com.vmware.vim25.mo.InventoryNavigator;
import com.vmware.vim25.mo.ManagedEntity;
import com.vmware.vim25.mo.ServiceInstance;
import com.vmware.vim25.mo.Task;
import com.vmware.vim25.mo.VirtualMachineSnapshot;

public class Main {

	static ManagedEntity[] hostsList = null;
	static String[] vmList = null;
	static vmObjec[] templateList = null;
	static vmObjec JC_ubuntu = null;

	public static void main(String[] args) throws Exception {
		ServiceInstance si = new ServiceInstance(new URL(setting.vCenterURL), setting.vCenterUser,
				setting.vCenterPassword, true);

		ManagedEntityNavigation();
		templateList = new vmObjec[vmList.length];
		for (int i = 0; i < vmList.length; i++) {
			int cursor = 0;
			if (vmList[i].toLowerCase().contains("template")) {
				templateList[cursor] = Methods.initializeVmByName(si, vmList[i]);
				cursor++;
			}
		}
		JC_ubuntu = Methods.initializeVmByName(si, "Team16_Ubuntu_v12_JC");
		System.out.println(JC_ubuntu.get_vm());
		Methods.createPwrAlarm(JC_ubuntu, JC_ubuntu.get_name() + "Alarm_JC", "keep vm power on.");

		//the monitor thread
		infoUpdate iu = new infoUpdate(JC_ubuntu);
		Thread update = new Thread(iu);
		update.start();

		//the Heartbeat and snapshot thread
		Thread hb = new Thread(new heartbeat(JC_ubuntu));
		hb.start();
		Thread ssThread = new Thread(new snapshotCache(JC_ubuntu));
		ssThread.start();

		System.out.println("Done");

	}

	public static class infoUpdate implements Runnable {
		private vmObjec targetVM;
		private boolean active = false;

		public infoUpdate(vmObjec vm) {
			this.targetVM = vm;
			this.active = true;
			System.out.println(this.targetVM.get_name());

		}

		public void setActive(Boolean set) {
			this.active = set;
		}

		public Boolean isActive() {
			return this.active;
		}

		public void run() {
			// TODO Auto-generated method stub

			try {
				PopertiesCollector.run(this.targetVM);
				this.active = false;
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
	}

	public static class heartbeat implements Runnable {
		private int pingAttempt = 0;
		private vmObjec targetVM;
		private boolean active = false;
		ManagedEntityStatus status = null;
		snapshotCache ssCreate;

		public heartbeat(vmObjec vm) {
			// ssCreate = new snapshotCache(vm);
			// Thread ssThread = new Thread(ssCreate);
			// ssThread.start();
			this.targetVM = vm;
			this.active = true;
		}

		public void setActive(Boolean set) {

			this.active = set;
		}

		public Boolean isActive() {
			return this.active;
		}

		public void run() {
			// TODO Auto-generated method stub
			while (active) {
				try {
					if (!Methods.IsReachable(this.targetVM.get_ip())) {
						System.out.print("Ping fail, attempt: " + (pingAttempt + 1)+"\n");
						pingAttempt++;
					}
					else {
						System.out.print("VM " + this.targetVM.get_name()+" is avlive.\n");
					}

					if (pingAttempt == 4) {
						VMrecovery(this.targetVM);
						this.setActive(false);
						// ssCreate.setActive(false);
						pingAttempt = 0;
						break;
					}

					Thread.currentThread();
					Thread.sleep(10);

				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}

	public static class snapshotCache implements Runnable {
		private vmObjec targetVM;
		private boolean active = false;
		ManagedEntityStatus status = null;
		private String snapshotname = "SStime:";
		private String desc = "Taken at ";

		public snapshotCache(vmObjec vm) {
			this.targetVM = vm;
			this.active = true;
		}

		public void setActive(Boolean set) {
			System.out.println("Snapshot was set: " + set);
			this.active = set;
		}

		public Boolean isActive() {
			return this.active;
		}

		public void run() {
			// TODO Auto-generated method stub
			while (active) {
				try {
					String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime());
					String SSname = snapshotname + timeStamp;

					Task task = this.targetVM.get_vm().createSnapshot_Task(SSname, desc + timeStamp, false, false);
					if (task.waitForMe() == Task.SUCCESS) {
						this.targetVM.set_LatestSanpshotName(SSname);
						System.out.println("Snapshot was created." + SSname);
					}
					Thread.currentThread();
					Thread.sleep(600000);

				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}

	public static void VMrecovery(vmObjec vm) {
		
		boolean isAlternative = false;
		Integer hostIndex = 0;
		String hostIP = null;
		try {
			// define the host that VM is mounting on.
			
			HostSystem hs = new HostSystem(vm.get_instance().getServerConnection(), vm.get_vm().getRuntime().getHost());
			hostIP = hs.getName();
			// if VM is failed, check the vHost is alive or not.
			if (Methods.IsReachable(hs.getName())) {
				System.out.println("Host is reachable...");
				try {
					HostConnectSpec hcs = new HostConnectSpec();
					hcs.setHostName(hostIP);
					hcs.setUserName(setting.vHostUser);
					hcs.setPassword(setting.vCenterPassword);
					Task recon = hs.reconnectHost_Task(hcs);
					// System.out.println("Try to reconnect host...");
					if (recon.waitForMe() == Task.SUCCESS) {
						Thread hb = new Thread(new heartbeat(vm));
						hb.start();
						System.out.println("Host :" + hs.getName() + " reconnect sucessfully!");
					}
				} catch (Exception e) {
					// e.printStackTrace();
				}
				/*
				 * If the vHost is alive, revert the VM to the latest snapshot
				 */
				VirtualMachineSnapshot vmsnap = Methods.getSnapshotInTree(vm.get_vm(), vm.get_LatestSanpshotName());
				if (vmsnap != null) {
					Task task = vmsnap.revertToSnapshot_Task(null);
					if (task.waitForMe() == Task.SUCCESS) {
						Thread hb = new Thread(new heartbeat(vm));
						hb.start();
						System.out.println("Reverted to snapshot:" + vm.get_LatestSanpshotName());
					}
				}
			} else {
				/*
				 * If the vHost is failed, try to reconnect to vCenter. If host
				 * is still dead, go over the Host list and find available one
				 */
				try {
					HostConnectSpec hcs = new HostConnectSpec();
					hcs.setHostName(hostIP);
					hcs.setUserName(setting.vHostUser);
					hcs.setPassword(setting.vCenterPassword);
					Task recon = hs.reconnectHost_Task(hcs);
					System.out.println("Try to reconnect host...");
					if (recon.waitForMe() == Task.SUCCESS) {
						Thread hb = new Thread(new heartbeat(vm));
						hb.start();
						System.out.println("Host :" + hs.getName() + " reconnect sucessfully!");
						return;
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				while (hostIndex <= hostsList.length) {
					if (hs.getName() != hostsList[hostIndex].getName()
							&& Methods.IsReachable(hostsList[hostIndex].getName())) {
						/*
						 * If alternative is available, do the cold migration
						 * and power on
						 */
						Methods.cloneVM(vm.get_name(), templateList[0], hostsList[hostIndex].getName());
						isAlternative = true;
						break;
					}
					hostIndex++;
				}
				if (!isAlternative) {
					// if cannot fixed host, just remove it and add a new one
					try {
						hs.destroy_Task();
						HostConnectSpec hcs = new HostConnectSpec();
						hcs.setHostName(hostIP);
						hcs.setUserName(setting.vHostUser);
						hcs.setPassword(setting.vCenterPassword);
						Task recon = hs.reconnectHost_Task(hcs);
						System.out.println("Try to reconnect host...");
						if (recon.waitForMe() == Task.SUCCESS) {
							Methods.cloneVM(vm.get_name(), templateList[0], hostIP);
							Thread hb = new Thread(new heartbeat(vm));
							hb.start();
							System.out.println("Host :" + hs.getName() + " readd sucessfully!");
							return;
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}

			}
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public static void ManagedEntityNavigation() {

		try {
			ServiceInstance si = new ServiceInstance(new URL(setting.vCenterURL), setting.vCenterUser,
					setting.vCenterPassword, true);
			Folder rootFolder = si.getRootFolder();
			hostsList = new InventoryNavigator(rootFolder).searchManagedEntities(new String[][] { { "HostSystem",
					"name" }, }, true);
			ManagedEntity[] vms = new InventoryNavigator(rootFolder).searchManagedEntities(new String[][] { {
					"VirtualMachine", "name" }, }, true);
			vmList = new String[vms.length];
			// ManagedEntityStatus mes = new ManagedEntityStatus();
			for (int i = 0; i < vms.length; i++) {
				vmList[i] = vms[i].getName();
				// System.out.println("vm[" + i + "]=" + vms[i].getName());
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

}
