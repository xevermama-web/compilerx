package compilerx.runtime;

public interface ExecutionIO {
    String readLine() throws InterruptedException;
    void write(String text);
}
