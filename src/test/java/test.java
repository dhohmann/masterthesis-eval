import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class test {

	@Test
	public void testJavaLibrary() {
		String[] paths = System.getProperty("java.library.path").split(";");
		for (String p : paths) {
			System.out.println(p);
		}
		assertTrue(true);
	}
}
