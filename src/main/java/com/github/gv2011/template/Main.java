package com.github.gv2011.template;

import static org.slf4j.LoggerFactory.getLogger;

import org.slf4j.Logger;


public class Main {

	private static final Logger LOG = getLogger(Main.class);

	public static void main(final String[] args) {
		final String msg = "¡Hola!";
		System.out.println(msg);
		LOG.info("Printed \"{}\".", msg);
	}

}
