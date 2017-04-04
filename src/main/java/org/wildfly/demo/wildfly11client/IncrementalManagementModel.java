

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class IncrementalManagementModel {
	
	private HashMap<String, Module> map;
	public IncrementalManagementModel() {
		map = new HashMap<String, Module>();
	}
	
	public void put(String id, Map<String, String> changedContent, List<String> removedContent) {
		Module m = new Module(changedContent, removedContent);
		map.put(id,  m);
	}
	
	public String[] keys() {
		Set<String> s = map.keySet();
		return (String[]) s.toArray(new String[s.size()]);
	}
	
	public Map<String,String> getChanged(String id) {
		Module m = map.get(id);
		if( m != null ) {
			return m.changedContent;
		}
		return null;
	}

	public List<String> getRemoved(String id) {
		Module m = map.get(id);
		if( m != null ) {
			return m.removedContent;
		}
		return null;
	}

	
	
	private static class Module {
		Map<String, String> changedContent;
		List<String> removedContent;
		public Module(Map<String, String> changedContent, List<String> removedContent) {
			this.changedContent = changedContent; 
			this.removedContent = removedContent;
		}
	}

}
