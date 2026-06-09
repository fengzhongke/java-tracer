package com.ali.trace.spy.jetty.vo;

/**
 * Progress info for async class retransform
 */
public class RetransformProgressVO {
    private int total;
    private int done;
    private int failed;
    private String status;

    public RetransformProgressVO(int total, int done, int failed, String status) {
        this.total = total;
        this.done = done;
        this.failed = failed;
        this.status = status;
    }

    public int getTotal() { return total; }
    public int getDone() { return done; }
    public int getFailed() { return failed; }
    public String getStatus() { return status; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"total\":").append(total);
        sb.append(",\"done\":").append(done);
        sb.append(",\"failed\":").append(failed);
        sb.append(",\"status\":\"").append(status).append("\"");
        sb.append("}");
        return sb.toString();
    }
}