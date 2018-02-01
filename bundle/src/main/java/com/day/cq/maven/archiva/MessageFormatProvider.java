package com.day.cq.maven.archiva;

import java.text.MessageFormat;

public interface MessageFormatProvider {
	
	MessageFormat getMessage(String messageName);

}
