/*
 * Copyright (c) 2005, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package sun.tools.attach;

import com.sun.tools.attach.VirtualMachineDescriptor;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.AttachPermission;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.spi.AttachProvider;

import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.io.File;

/*
 * Platform specific provider implementations extend this
 *
 * Modified: removed sun.jvmstat.monitor dependency.
 * listVirtualMachines() now scans /tmp for .java_pid<pid> socket files
 * to find running HotSpot JVMs on Linux, which is the same mechanism
 * used by the attach protocol itself.
 */
public abstract class HotSpotAttachProvider extends AttachProvider {

    // "/tmp" is used as a global well-known location for the files
    // .java_pid<pid>. and .attach_pid<pid>.
    private static final String tmpdir = "/tmp";

    public HotSpotAttachProvider() {
    }

    public void checkAttachPermission() {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(
                new AttachPermission("attachVirtualMachine")
            );
        }
    }

    /*
     * This listVirtualMachines implementation scans the /tmp directory
     * for .java_pid<pid> socket files which indicate running HotSpot JVMs.
     * This is the same mechanism used by the attach protocol.
     */
    public List<VirtualMachineDescriptor> listVirtualMachines() {
        ArrayList<VirtualMachineDescriptor> result =
            new ArrayList<VirtualMachineDescriptor>();

        File tmpDir = new File(tmpdir);
        if (!tmpDir.isDirectory()) {
            return result;
        }

        String[] files = tmpDir.list();
        if (files == null) {
            return result;
        }

        for (String file : files) {
            if (file.startsWith(".java_pid")) {
                String pidStr = file.substring(".java_pid".length());
                try {
                    int pid = Integer.parseInt(pidStr);
                    // Check if the process still exists by checking /proc/<pid>
                    File procDir = new File("/proc/" + pid);
                    if (procDir.isDirectory()) {
                        // Read the process command line for display name
                        String displayName = getProcessCommandLine(pid);
                        if (displayName == null) {
                            displayName = pidStr;
                        }
                        result.add(new HotSpotVirtualMachineDescriptor(this, pidStr, displayName));
                    }
                } catch (NumberFormatException e) {
                    // ignore files that don't match the expected pattern
                }
            }
        }
        return result;
    }

    /**
     * Read the process command line from /proc/<pid>/cmdline
     */
    private String getProcessCommandLine(int pid) {
        try {
            File cmdlineFile = new File("/proc/" + pid + "/cmdline");
            if (!cmdlineFile.exists()) {
                return null;
            }
            java.io.FileInputStream fis = new java.io.FileInputStream(cmdlineFile);
            try {
                byte[] buf = new byte[256];
                int n = fis.read(buf);
                if (n > 0) {
                    // cmdline has args separated by null bytes
                    String cmdline = new String(buf, 0, n, "UTF-8");
                    cmdline = cmdline.replace('\0', ' ').trim();
                    return cmdline;
                }
            } finally {
                fis.close();
            }
        } catch (IOException e) {
            // ignore
        }
        return null;
    }

    /**
     * A virtual machine descriptor to describe a HotSpot virtual machine.
     */
    static class HotSpotVirtualMachineDescriptor extends VirtualMachineDescriptor {
        HotSpotVirtualMachineDescriptor(AttachProvider provider,
                                        String id,
                                        String displayName) {
            super(provider, id, displayName);
        }

        public boolean isAttachable() {
            return true;
        }
    }
}