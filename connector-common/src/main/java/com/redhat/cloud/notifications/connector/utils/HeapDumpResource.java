package com.redhat.cloud.notifications.connector.utils;

import com.sun.management.HotSpotDiagnosticMXBean;
import io.quarkus.logging.Log;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import org.apache.http.HttpStatus;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.RestPath;
import javax.management.MBeanServer;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;

import static jakarta.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM;

@Path("/heap_dump")
public class HeapDumpResource {

    @ConfigProperty(name = "quarkus.log.cloudwatch.log-stream-name", defaultValue = "localhost")
    String hostname;

    @GET
    @Path("{hostname}")
    @Produces(APPLICATION_OCTET_STREAM)
    public Response getHeapDump(@RestPath String hostname) {
        if (!this.hostname.equals(hostname)) {
            Log.infof("%s doesn't match with this host (%s)", hostname, this.hostname);
            return Response.status(HttpStatus.SC_SEE_OTHER).build();
        }

        String tmpFileName = "notifications_dump.hprof";
        String fullPathTmpFile = String.format("%s/%s", System.getProperty("java.io.tmpdir"), tmpFileName);

        File file = new File(fullPathTmpFile);
        file.delete();

        Log.infof("Heap dump will be generated on %s", fullPathTmpFile);
        try {
            dumpHeap(fullPathTmpFile);
        } catch (IOException ie) {
            Log.error("Error generating heap dump", ie);
        }
        file = new File(fullPathTmpFile);
        try {
            file.deleteOnExit();
        } catch (SecurityException e) {
            Log.warn("Delete on exit of the notifications heap dump file denied by the security manager", e);
        }
        Response.ResponseBuilder response = Response.ok(file);
        response.header("Content-Disposition", "attachment; filename=\"" + tmpFileName + "\"");
        return response.build();
    }

    public static void dumpHeap(String filePath) throws IOException {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        HotSpotDiagnosticMXBean mxBean = ManagementFactory.newPlatformMXBeanProxy(
            server, "com.sun.management:type=HotSpotDiagnostic", HotSpotDiagnosticMXBean.class);
        mxBean.dumpHeap(filePath, true);
    }
}
