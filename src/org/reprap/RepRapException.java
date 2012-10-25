package org.reprap;

public class RepRapException extends Exception {
	static final long serialVersionUID = 0;
	int state = 0;

	public RepRapException() {
		super();
	}

	public RepRapException(String arg0) {
		super(arg0);
	}

	public RepRapException(Throwable arg0) {
		super(arg0);
	}
	
	public RepRapException(int s) {
		super();
		state = s;
	}
	
	public int eState()
	{
		return state;
	}

}
