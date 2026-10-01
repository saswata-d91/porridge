public class TestAgy {
    public static void main(String[] args) throws Exception {
        Process p = new ProcessBuilder("agy", "--print", "hello")
            .inheritIO()
            .start();
        p.waitFor();
    }
}
