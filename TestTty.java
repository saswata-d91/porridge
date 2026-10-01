public class TestTty {
    public static void main(String[] args) throws Exception {
        Process p = new ProcessBuilder("agy", "--print", "hello")
            .redirectInput(ProcessBuilder.Redirect.INHERIT)
            .redirectErrorStream(true)
            .start();
        java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println(line);
        }
        p.waitFor();
    }
}
