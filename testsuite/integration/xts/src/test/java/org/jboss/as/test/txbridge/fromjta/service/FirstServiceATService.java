/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.test.txbridge.fromjta.service;

import javax.xml.namespace.QName;
import javax.xml.ws.Service;
import javax.xml.ws.WebEndpoint;
import javax.xml.ws.WebServiceClient;
import javax.xml.ws.WebServiceFeature;

import org.jboss.logging.Logger;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * This class was generated by the JAX-WS RI. JAX-WS RI 2.1.6 in JDK 6 Generated source version: 2.1
 */
@WebServiceClient(name = "FirstServiceATService", targetNamespace = "http://www.jboss.com/jbossas/test/txbridge/fromjta/first")
public class FirstServiceATService extends Service {
    private static final Logger log = Logger.getLogger(FirstServiceATService.class.getName());

    private static final URL FIRSTSERVICEATSERVICE_WSDL_LOCATION;

    static {
        URL url = null;
        try {
            URL baseUrl;
            baseUrl = FirstServiceATService.class.getResource(".");
            url = new URL(baseUrl, "FirstServiceAT.wsdl");
        } catch (MalformedURLException e) {
            log.warn("Failed to create URL for the wsdl Location: 'FirstServiceAT.wsdl', retrying as a local file", e);
        }
        FIRSTSERVICEATSERVICE_WSDL_LOCATION = url;
    }

    public FirstServiceATService(URL wsdlLocation, QName serviceName) {
        super(wsdlLocation, serviceName);
    }

    public FirstServiceATService() {
        super(FIRSTSERVICEATSERVICE_WSDL_LOCATION,
            new QName("http://www.jboss.com/jbossas/test/txbridge/fromjta/First", "FirstServiceATService"));
    }

    @WebEndpoint(name = "FirstServiceAT")
    public FirstServiceAT getFirstServiceAT() {
        return super.getPort(
            new QName("http://www.jboss.com/jbossas/test/txbridge/fromjta/First", "FirstServiceAT"),
            FirstServiceAT.class);
    }

    /**
     * @param features A list of {@link javax.xml.ws.WebServiceFeature} to configure on the proxy.
     *   Supported features not in the <code>features</code> parameter will have their default values.
     */
    @WebEndpoint(name = "FirstServiceAT")
    public FirstServiceAT getFirstServiceAT(WebServiceFeature... features) {
        return super.getPort(
            new QName("http://www.jboss.com/jbossas/test/txbridge/fromjta/First", "FirstServiceAT"),
            FirstServiceAT.class, features);
    }

}
