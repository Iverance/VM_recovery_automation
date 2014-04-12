package edu.sjsu.chengJu;

import com.vmware.vim25.mo.Folder;
import com.vmware.vim25.mo.ServiceInstance;
import com.vmware.vim25.mo.VirtualMachine;

public class vmObjec {
	
	private String vmname = null;
	private ServiceInstance si;
	private VirtualMachine vm = null;
	private Folder rootFolder = null; 
	private String ip = "";
	private String LatestSnapshotName = "";
	
	public void set_name(String name) {
		this.vmname = name;
	}
	public void set_instance(ServiceInstance si) {
		this.si = si;
	}
	
	public void set_vm(VirtualMachine vm) {
		this.vm = vm;
	}
	
	public void set_rootFolder(Folder rf) {
		this.rootFolder = rf;
	}
	
	public void set_ip(String ip) {
		this.ip = ip;
	}
	
	public void set_LatestSanpshotName(String LSN) {
		this.LatestSnapshotName = LSN;
	}
	
	public String get_name() {
		return this.vmname;
	}
	
	public ServiceInstance get_instance() {
		return this.si;
	}
	
	public VirtualMachine get_vm() {
		return this.vm;
	}
	
	public Folder get_rootFolder() {
		return this.rootFolder;
	}
	
	public String get_ip() {
		return this.ip;
	}
	
	public String get_LatestSanpshotName() {
		return this.LatestSnapshotName;
	}
	
}
