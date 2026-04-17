package unsa.st.com.pkg;

import java.util.ArrayList;
import java.util.List;

public class PackageInfo {
    public String packageName;
    public String version;
    public String architecture;
    public String filename;
    public long size;
    public String md5;
    public String sha256;
    public String description;
    public List<String> depends = new ArrayList<>();

    public static PackageInfo parse(String block) {
        PackageInfo info = new PackageInfo();
        String[] lines = block.split("\n");
        for (String line : lines) {
            if (line.startsWith("Package: ")) {
                info.packageName = line.substring(9).trim();
            } else if (line.startsWith("Version: ")) {
                info.version = line.substring(9).trim();
            } else if (line.startsWith("Architecture: ")) {
                info.architecture = line.substring(14).trim();
            } else if (line.startsWith("Filename: ")) {
                info.filename = line.substring(10).trim();
            } else if (line.startsWith("Size: ")) {
                try { info.size = Long.parseLong(line.substring(6).trim()); } catch (NumberFormatException ignored) {}
            } else if (line.startsWith("MD5sum: ")) {
                info.md5 = line.substring(8).trim();
            } else if (line.startsWith("SHA256: ")) {
                info.sha256 = line.substring(8).trim();
            } else if (line.startsWith("Description: ")) {
                info.description = line.substring(13).trim();
            } else if (line.startsWith("Depends: ")) {
                String deps = line.substring(9).trim();
                for (String dep : deps.split(",")) {
                    info.depends.add(dep.trim());
                }
            }
        }
        return info;
    }

    @Override
    public String toString() {
        return packageName + " (" + version + ")";
    }
}
