package edu.sjsu.chengJu;

import java.net.URL;
import java.util.ArrayList;

import com.vmware.vim25.Action;
import com.vmware.vim25.AlarmAction;
import com.vmware.vim25.AlarmSetting;
import com.vmware.vim25.AlarmSpec;
import com.vmware.vim25.AlarmTriggeringAction;
import com.vmware.vim25.GroupAlarmAction;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.MethodAction;
import com.vmware.vim25.MethodActionArgument;
import com.vmware.vim25.StateAlarmExpression;
import com.vmware.vim25.StateAlarmOperator;
import com.vmware.vim25.VirtualDevice;
import com.vmware.vim25.VirtualDeviceBackingInfo;
import com.vmware.vim25.VirtualDisk;
import com.vmware.vim25.VirtualDiskFlatVer1BackingInfo;
import com.vmware.vim25.VirtualDiskFlatVer2BackingInfo;
import com.vmware.vim25.VirtualDiskRawDiskMappingVer1BackingInfo;
import com.vmware.vim25.VirtualDiskSparseVer1BackingInfo;
import com.vmware.vim25.VirtualDiskSparseVer2BackingInfo;
import com.vmware.vim25.VirtualMachineCloneSpec;
import com.vmware.vim25.VirtualMachineRelocateSpec;
import com.vmware.vim25.VirtualMachineSnapshotTree;
import com.vmware.vim25.mo.AlarmManager;
import com.vmware.vim25.mo.Datacenter;
import com.vmware.vim25.mo.Folder;
import com.vmware.vim25.mo.HostSystem;
import com.vmware.vim25.mo.InventoryNavigator;
import com.vmware.vim25.mo.ResourcePool;
import com.vmware.vim25.mo.ServiceInstance;
import com.vmware.vim25.mo.Task;
import com.vmware.vim25.mo.VirtualMachine;
import com.vmware.vim25.mo.VirtualMachineSnapshot;

public class Methods {
	public static void createPwrAlarm(vmObjec vm, String alarmName, String desc) {
		if (vm.get_vm() == null) {
			System.out.println("Cannot find the VM " + vm.get_name()
					+ "Existing...\n Try to initialize VM object first.");
			return;
		}

		AlarmManager alarmMgr = vm.get_instance().getAlarmManager();
		AlarmSpec spec = new AlarmSpec();

		StateAlarmExpression expression = createStateAlarmExpression();
		AlarmAction methodAction = createAlarmTriggerAction(createPowerOnAction());
		GroupAlarmAction gaa = new GroupAlarmAction();

		gaa.setAction(new AlarmAction[] { methodAction });
		spec.setAction(gaa);
		spec.setExpression(expression);
		spec.setName(alarmName);
		spec.setDescription(desc);
		spec.setEnabled(true);

		AlarmSetting as = new AlarmSetting();
		as.setReportingFrequency(0); // as often as possible
		as.setToleranceRange(0);

		spec.setSetting(as);

		try {
			alarmMgr.createAlarm(vm.get_vm(), spec);
		} catch (Exception e) {
			System.out.print("Error: " + e.toString());
		}

	}

	static StateAlarmExpression createStateAlarmExpression() {
		StateAlarmExpression expression = new StateAlarmExpression();
		expression.setType("VirtualMachine");
		expression.setStatePath("runtime.powerState");
		expression.setOperator(StateAlarmOperator.isEqual);
		expression.setRed("poweredOff");
		return expression;
	}

	static MethodAction createPowerOnAction() {
		MethodAction action = new MethodAction();
		action.setName("PowerOnVM_Task");
		MethodActionArgument argument = new MethodActionArgument();
		argument.setValue(null);
		action.setArgument(new MethodActionArgument[] { argument });
		return action;
	}

	static AlarmTriggeringAction createAlarmTriggerAction(Action action) {
		AlarmTriggeringAction alarmAction = new AlarmTriggeringAction();
		alarmAction.setYellow2red(true);
		alarmAction.setAction(action);
		return alarmAction;
	}

	public static vmObjec initializeVmByName(ServiceInstance si, String vmname) {
		// initialise instance variables
		vmObjec vm = null;
		try {
			vm = new vmObjec();
			vm.set_instance(si);
			vm.set_rootFolder(si.getRootFolder());
			vm.set_vm((VirtualMachine) new InventoryNavigator(vm.get_rootFolder()).searchManagedEntity(
					"VirtualMachine", vmname));
			vm.set_name(vmname);
			vm.set_ip((String) vm.get_vm().getGuest().getIpAddress());
			System.out.println("VM: " + vm.get_name() + " found\nIP: " + vm.get_ip());
		} catch (Exception e) {
			System.out.println(e.toString());
		}

		if (vm.get_name() == null) {
			System.out.println("No VM " + vmname + " found");
			vm = null;
		}
		return vm;
	}

	public static Boolean IsReachable(String host) {
		Boolean result = null;
		try {
			String cmd = "";
			if (System.getProperty("os.name").startsWith("Windows")) {
				// For Windows
				cmd = "ping -n 1 " + host;
			} else {
				// For Linux and OSX
				cmd = "ping -c 1 " + host;
			}

			Process myProcess = Runtime.getRuntime().exec(cmd);
			myProcess.waitFor();

			if (myProcess.exitValue() == 0) {

				result = true;
			} else {

				result = false;
			}

		} catch (Exception e) {

			e.printStackTrace();
		}
		return result;
	}

	static VirtualMachineSnapshot getSnapshotInTree(VirtualMachine vm, String snapName) {
		if (vm == null || snapName == null) {
			return null;
		}

		VirtualMachineSnapshotTree[] snapTree = vm.getSnapshot().getRootSnapshotList();
		if (snapTree != null) {
			ManagedObjectReference mor = findSnapshotInTree(snapTree, snapName);
			if (mor != null) {
				return new VirtualMachineSnapshot(vm.getServerConnection(), mor);
			}
		}
		return null;
	}

	static ManagedObjectReference findSnapshotInTree(VirtualMachineSnapshotTree[] snapTree, String snapName) {
		for (int i = 0; i < snapTree.length; i++) {
			VirtualMachineSnapshotTree node = snapTree[i];
			if (snapName.equals(node.getName())) {
				return node.getSnapshot();
			} else {
				VirtualMachineSnapshotTree[] childTree = node.getChildSnapshotList();
				if (childTree != null) {
					ManagedObjectReference mor = findSnapshotInTree(childTree, snapName);
					if (mor != null) {
						return mor;
					}
				}
			}
		}
		return null;
	}

	static void cloneVM(String cloneName, vmObjec tmpobj, String Host) {
		String vmname = tmpobj.get_name();

		try {
			Folder rootFolder = tmpobj.get_instance().getRootFolder();
			VirtualMachine vm = (VirtualMachine) new InventoryNavigator(rootFolder).searchManagedEntity(
					"VirtualMachine", vmname);
			HostSystem hs = (HostSystem) new InventoryNavigator(rootFolder).searchManagedEntity("HostSystem", Host);
			Datacenter dc = (Datacenter) new InventoryNavigator(rootFolder).searchManagedEntity("Datacenter", "T16_DC");
			ResourcePool rp = (ResourcePool) new InventoryNavigator(dc).searchManagedEntities("ResourcePool")[0];

			if (vm == null) {
				System.out.println("No VM " + vmname + " found");
				tmpobj.get_instance().getServerConnection().logout();
				return;
			}

			VirtualMachineCloneSpec cloneSpec = new VirtualMachineCloneSpec();
			VirtualMachineRelocateSpec vmrs = new VirtualMachineRelocateSpec();

			vmrs.setHost(hs.getMOR());
			vmrs.setPool(rp.getMOR());
			
			cloneSpec.setLocation(vmrs);
			cloneSpec.setPowerOn(true);
			cloneSpec.setTemplate(false);

			Task task = vm.cloneVM_Task((Folder) vm.getParent(), cloneName, cloneSpec);
			System.out.println("Launching the VM clone task. " + "Please wait ...");

			String status = task.waitForMe();
			if (status == Task.SUCCESS) {
				System.out.println("VM got cloned successfully.");
			} else {
				System.out.println("Failure -: VM cannot be cloned");
			}
		} catch (Exception e) {
			System.out.println(e.toString());
		}
	}

	private static ArrayList<Integer> getIndependenetVirtualDiskKeys(VirtualMachine vm) throws Exception {
		ArrayList<Integer> diskKeys = new ArrayList<Integer>();

		VirtualDevice[] devices = (VirtualDevice[]) vm.getPropertyByPath("config.hardware.device");

		for (int i = 0; i < devices.length; i++) {
			if (devices[i] instanceof VirtualDisk) {
				VirtualDisk vDisk = (VirtualDisk) devices[i];
				String diskMode = "";
				VirtualDeviceBackingInfo vdbi = vDisk.getBacking();

				if (vdbi instanceof VirtualDiskFlatVer1BackingInfo) {
					diskMode = ((VirtualDiskFlatVer1BackingInfo) vdbi).getDiskMode();
				} else if (vdbi instanceof VirtualDiskFlatVer2BackingInfo) {
					diskMode = ((VirtualDiskFlatVer2BackingInfo) vdbi).getDiskMode();
				} else if (vdbi instanceof VirtualDiskRawDiskMappingVer1BackingInfo) {
					diskMode = ((VirtualDiskRawDiskMappingVer1BackingInfo) vdbi).getDiskMode();
				} else if (vdbi instanceof VirtualDiskSparseVer1BackingInfo) {
					diskMode = ((VirtualDiskSparseVer1BackingInfo) vdbi).getDiskMode();
				} else if (vdbi instanceof VirtualDiskSparseVer2BackingInfo) {
					diskMode = ((VirtualDiskSparseVer2BackingInfo) vdbi).getDiskMode();
				}

				if (diskMode.indexOf("independent") != -1) {
					diskKeys.add(vDisk.getKey());
				}
			}
		}
		return diskKeys;
	}

}
