import org.example.utils.ImageUtils;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ImageUtilsTests {
    private final String sixteenBySixteenBlack = "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAIAAACQkWg2AAABhGlDQ1BJQ0MgcHJvZmlsZQAAKJF9kT1Iw1AUhU9TpSIVBzuIdMhQXbSLijjWKhShQqgVWnUweekfNGlIUlwcBdeCgz+LVQcXZ10dXAVB8AfE1cVJ0UVKvC8ptIjxwuN9nHfP4b37AKFZZZrVkwA03TYzqaSYy6+KoVcIiCKAMMZlZhlzkpSGb33dUyfVXZxn+ff9WQNqwWJAQCROMMO0iTeIZzZtg/M+cYSVZZX4nHjCpAsSP3Jd8fiNc8llgWdGzGxmnjhCLJa6WOliVjY14mnimKrplC/kPFY5b3HWqnXWvid/YbigryxznVYUKSxiCRJEKKijgipsxGnXSbGQofOkj3/E9UvkUshVASPHAmrQILt+8D/4PVurODXpJYWTQO+L43yMAqFdoNVwnO9jx2mdAMFn4Erv+GtNYPaT9EZHix0Bg9vAxXVHU/aAyx1g+MmQTdmVgrSEYhF4P6NvygNDt0D/mje39jlOH4AszSp9AxwcAmMlyl73eXdf99z+7WnP7wdx9nKmVkSfiwAAAAlwSFlzAAAuIwAALiMBeKU/dgAAAAd0SU1FB+cCHAENH57+I1IAAAAZdEVYdENvbW1lbnQAQ3JlYXRlZCB3aXRoIEdJTVBXgQ4XAAAAEElEQVQoz2NgGAWjYBTAAAADEAABaJFtwwAAAABJRU5ErkJggg==";
    private final String fourByFourBlack = "iVBORw0KGgoAAAANSUhEUgAAAAQAAAAECAIAAAAmkwkpAAABhGlDQ1BJQ0MgcHJvZmlsZQAAKJF9kT1Iw1AUhU9TpSIVBzuIdMhQXbSLijjWKhShQqgVWnUweekfNGlIUlwcBdeCgz+LVQcXZ10dXAVB8AfE1cVJ0UVKvC8ptIjxwuN9nHfP4b37AKFZZZrVkwA03TYzqaSYy6+KoVcIiCKAMMZlZhlzkpSGb33dUyfVXZxn+ff9WQNqwWJAQCROMMO0iTeIZzZtg/M+cYSVZZX4nHjCpAsSP3Jd8fiNc8llgWdGzGxmnjhCLJa6WOliVjY14mnimKrplC/kPFY5b3HWqnXWvid/YbigryxznVYUKSxiCRJEKKijgipsxGnXSbGQofOkj3/E9UvkUshVASPHAmrQILt+8D/4PVurODXpJYWTQO+L43yMAqFdoNVwnO9jx2mdAMFn4Erv+GtNYPaT9EZHix0Bg9vAxXVHU/aAyx1g+MmQTdmVgrSEYhF4P6NvygNDt0D/mje39jlOH4AszSp9AxwcAmMlyl73eXdf99z+7WnP7wdx9nKmVkSfiwAAAAlwSFlzAAAuIwAALiMBeKU/dgAAAAd0SU1FB+cCHAENOUzzpq8AAAAZdEVYdENvbW1lbnQAQ3JlYXRlZCB3aXRoIEdJTVBXgQ4XAAAADElEQVQI12NgIB0AAAA0AAGnfbGcAAAAAElFTkSuQmCC";
    private final String sixteenByTwentyFourBlack = "iVBORw0KGgoAAAANSUhEUgAAABAAAAAYCAIAAAB8wupbAAABhGlDQ1BJQ0MgcHJvZmlsZQAAKJF9kT1Iw1AUhU9TpSIVBzuIdMhQXbSLijjWKhShQqgVWnUweekfNGlIUlwcBdeCgz+LVQcXZ10dXAVB8AfE1cVJ0UVKvC8ptIjxwuN9nHfP4b37AKFZZZrVkwA03TYzqaSYy6+KoVcIiCKAMMZlZhlzkpSGb33dUyfVXZxn+ff9WQNqwWJAQCROMMO0iTeIZzZtg/M+cYSVZZX4nHjCpAsSP3Jd8fiNc8llgWdGzGxmnjhCLJa6WOliVjY14mnimKrplC/kPFY5b3HWqnXWvid/YbigryxznVYUKSxiCRJEKKijgipsxGnXSbGQofOkj3/E9UvkUshVASPHAmrQILt+8D/4PVurODXpJYWTQO+L43yMAqFdoNVwnO9jx2mdAMFn4Erv+GtNYPaT9EZHix0Bg9vAxXVHU/aAyx1g+MmQTdmVgrSEYhF4P6NvygNDt0D/mje39jlOH4AszSp9AxwcAmMlyl73eXdf99z+7WnP7wdx9nKmVkSfiwAAAAlwSFlzAAAuIwAALiMBeKU/dgAAAAd0SU1FB+cCHAEOFswPyDUAAAAZdEVYdENvbW1lbnQAQ3JlYXRlZCB3aXRoIEdJTVBXgQ4XAAAAEklEQVQ4y2NgGAWjYBSMgsEFAASYAAEwai57AAAAAElFTkSuQmCC";
    private final String twentyFourBySixteenBlack = "iVBORw0KGgoAAAANSUhEUgAAABgAAAAQCAIAAACDRijCAAABhGlDQ1BJQ0MgcHJvZmlsZQAAKJF9kT1Iw1AUhU9TpSIVBzuIdMhQXbSLijjWKhShQqgVWnUweekfNGlIUlwcBdeCgz+LVQcXZ10dXAVB8AfE1cVJ0UVKvC8ptIjxwuN9nHfP4b37AKFZZZrVkwA03TYzqaSYy6+KoVcIiCKAMMZlZhlzkpSGb33dUyfVXZxn+ff9WQNqwWJAQCROMMO0iTeIZzZtg/M+cYSVZZX4nHjCpAsSP3Jd8fiNc8llgWdGzGxmnjhCLJa6WOliVjY14mnimKrplC/kPFY5b3HWqnXWvid/YbigryxznVYUKSxiCRJEKKijgipsxGnXSbGQofOkj3/E9UvkUshVASPHAmrQILt+8D/4PVurODXpJYWTQO+L43yMAqFdoNVwnO9jx2mdAMFn4Erv+GtNYPaT9EZHix0Bg9vAxXVHU/aAyx1g+MmQTdmVgrSEYhF4P6NvygNDt0D/mje39jlOH4AszSp9AxwcAmMlyl73eXdf99z+7WnP7wdx9nKmVkSfiwAAAAlwSFlzAAAuIwAALiMBeKU/dgAAAAd0SU1FB+cCHAEOK5RnhCQAAAAZdEVYdENvbW1lbnQAQ3JlYXRlZCB3aXRoIEdJTVBXgQ4XAAAAEklEQVQ4y2NgGAWjYBSMgsEBAASQAAGl6D3VAAAAAElFTkSuQmCC";
    private final String fifteenByNineteenBlack = "iVBORw0KGgoAAAANSUhEUgAAAA8AAAATCAIAAADAoMD9AAABhGlDQ1BJQ0MgcHJvZmlsZQAAKJF9kT1Iw1AUhU9TpSIVBzuIdMhQXbSLijjWKhShQqgVWnUweekfNGlIUlwcBdeCgz+LVQcXZ10dXAVB8AfE1cVJ0UVKvC8ptIjxwuN9nHfP4b37AKFZZZrVkwA03TYzqaSYy6+KoVcIiCKAMMZlZhlzkpSGb33dUyfVXZxn+ff9WQNqwWJAQCROMMO0iTeIZzZtg/M+cYSVZZX4nHjCpAsSP3Jd8fiNc8llgWdGzGxmnjhCLJa6WOliVjY14mnimKrplC/kPFY5b3HWqnXWvid/YbigryxznVYUKSxiCRJEKKijgipsxGnXSbGQofOkj3/E9UvkUshVASPHAmrQILt+8D/4PVurODXpJYWTQO+L43yMAqFdoNVwnO9jx2mdAMFn4Erv+GtNYPaT9EZHix0Bg9vAxXVHU/aAyx1g+MmQTdmVgrSEYhF4P6NvygNDt0D/mje39jlOH4AszSp9AxwcAmMlyl73eXdf99z+7WnP7wdx9nKmVkSfiwAAAAlwSFlzAAAuIwAALiMBeKU/dgAAAAd0SU1FB+cCHAEPBsij6RAAAAAZdEVYdENvbW1lbnQAQ3JlYXRlZCB3aXRoIEdJTVBXgQ4XAAAAEUlEQVQoz2NgGAWjYBTQHgAAA2oAAbUN9tIAAAAASUVORK5CYII=";
    @Test
    void compareOfSameSize(){
        byte[] testImage = Base64.getDecoder().decode(sixteenBySixteenBlack);

        assertTrue(ImageUtils.compareImages(testImage, testImage, 100), "Comparing identical image returned false.");
    }

    @Test
    void compareDifferentSizeSameAspectRatio(){
        byte[] larger = Base64.getDecoder().decode(sixteenBySixteenBlack);
        byte[] smaller = Base64.getDecoder().decode(fourByFourBlack);

        assertTrue(ImageUtils.compareImages(larger, smaller, 100), "Comparing identical images of different size, but with same aspect ratio returned false.");
    }

    @Test
    void compareHigher(){
        byte[] higher = Base64.getDecoder().decode(sixteenByTwentyFourBlack);
        byte[] normal = Base64.getDecoder().decode(sixteenBySixteenBlack);

        assertTrue(ImageUtils.compareImages(higher, normal, 100), "Comparing identical 16x16 and 16x24 images returned false.");
    }

    @Test
    void compareWider(){
        byte[] wider = Base64.getDecoder().decode(twentyFourBySixteenBlack);
        byte[] normal = Base64.getDecoder().decode(sixteenBySixteenBlack);

        assertTrue(ImageUtils.compareImages(wider, normal, 100), "Comparing identical 16x16 and 24x16 images returned false.");
    }

    @Test
    void compareWiderHigher(){
        byte[] wider = Base64.getDecoder().decode(twentyFourBySixteenBlack);
        byte[] higher = Base64.getDecoder().decode(sixteenByTwentyFourBlack);

        assertTrue(ImageUtils.compareImages(wider, higher, 100), "Comparing identical 16x24 and 24x16 images returned false.");
    }

    @Test
    void comparePrime(){
        byte[] prime = Base64.getDecoder().decode(fifteenByNineteenBlack);
        byte[] normal = Base64.getDecoder().decode(sixteenBySixteenBlack);

        assertTrue(ImageUtils.compareImages(prime, normal, 100), "Comparing identical 16x16 and 15x19 images returned false.");
    }
}
