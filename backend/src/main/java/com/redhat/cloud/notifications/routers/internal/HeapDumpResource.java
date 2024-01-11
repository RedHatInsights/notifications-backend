package com.redhat.cloud.notifications.routers.internal;

import io.quarkus.logging.Log;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static com.redhat.cloud.notifications.auth.ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN;
import static jakarta.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM;

@Path(API_INTERNAL + "/heap_dump")
@RolesAllowed(RBAC_INTERNAL_ADMIN)
public class HeapDumpResource {

    @GET
    @Produces(APPLICATION_OCTET_STREAM)
    public Response getHeapDump() {
        long pid = ProcessHandle.current().pid();
        String tmpFileName = "notifications_dump.hprof";
        String fullPathTmpFile = String.format("%s/%s", System.getProperty("java.io.tmpdir"), tmpFileName);

        File file = new File(fullPathTmpFile);
        file.delete();

        Log.infof("Heap dump will be generated on %s", fullPathTmpFile);

        ProcessBuilder processBuilder = new ProcessBuilder();

        // Run shell heap dump command
        processBuilder.command("bash", "-c", String.format("jcmd %d GC.heap_dump %s", pid, fullPathTmpFile));

        try {

            Process process = processBuilder.start();
            StringBuilder output = new StringBuilder();
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()));

            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line + "\n");
            }

            int exitVal = process.waitFor();
            if (exitVal == 0) {
                Log.info("Heap dump was successfully generated");
            } else {
                Log.info("Error generating Heap dump");
            }
            Log.info(output);

        } catch (IOException | InterruptedException e) {
            Log.error("Error generating heap dump", e);
        }

        file = new File(fullPathTmpFile);

        Response.ResponseBuilder response = Response.ok(file);
        response.header("Content-Disposition", "attachment; filename=\"" + tmpFileName + "\"");
        return response.build();
    }
}
