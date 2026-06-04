package com.ali.trace.spy.jetty.vo;

import java.util.ArrayList;
import java.util.List;

/**
 * Root VO for package statistics
 */
public class PackageRootVO {
    private int loaderCount;
    private int totalClasses;
    private int totalWoven;
    private int totalNotWoven;
    private List<PackageNodeVO> packages = new ArrayList<PackageNodeVO>();

    public int getLoaderCount() {
        return loaderCount;
    }

    public void setLoaderCount(int loaderCount) {
        this.loaderCount = loaderCount;
    }

    public int getTotalClasses() {
        return totalClasses;
    }

    public void setTotalClasses(int totalClasses) {
        this.totalClasses = totalClasses;
    }

    public int getTotalWoven() {
        return totalWoven;
    }

    public void setTotalWoven(int totalWoven) {
        this.totalWoven = totalWoven;
    }

    public int getTotalNotWoven() {
        return totalNotWoven;
    }

    public void setTotalNotWoven(int totalNotWoven) {
        this.totalNotWoven = totalNotWoven;
    }

    public List<PackageNodeVO> getPackages() {
        return packages;
    }

    public void setPackages(List<PackageNodeVO> packages) {
        this.packages = packages;
    }

    public void addPackage(PackageNodeVO pkg) {
        this.packages.add(pkg);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"loaderCount\":").append(loaderCount);
        sb.append(",\"totalClasses\":").append(totalClasses);
        sb.append(",\"totalWoven\":").append(totalWoven);
        sb.append(",\"totalNotWoven\":").append(totalNotWoven);
        sb.append(",\"packages\":").append(packages);
        sb.append("}");
        return sb.toString();
    }
}