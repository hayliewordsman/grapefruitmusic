package com.android.inputmethod.keyboard;

import android.content.Context;
import com.android.inputmethod.latin.DictionaryFactory;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.SubtypeLocale;
import java.io.InputStream;
import java.util.Locale;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={17,19})
public class ResourceNamespaceTest {
    @Test public void installedAppCanResolveAndReadAllBundledDictionaries() throws Exception {
        Context c=RuntimeEnvironment.getApplication();
        Locale[] locales={Locale.US,Locale.GERMAN,Locale.FRENCH,new Locale("es"),
                Locale.ITALIAN,new Locale("pt","BR"),new Locale("ru")};
        for(Locale locale:locales) {
            assertTrue(locale.toString(),DictionaryFactory.isDictionaryAvailable(c,locale));
            int id=DictionaryFactory.getMainDictionaryResourceId(c.getResources(),locale);
            assertTrue(locale.toString(),id!=0);
            InputStream stream=c.getResources().openRawResource(id);
            try { assertTrue(stream.read()!=-1); } finally { stream.close(); }
        }
    }
    @Test public void unknownLocaleGetsTheBundledFallbackInsteadOfResourceZero() {
        Context c=RuntimeEnvironment.getApplication();
        assertEquals(R.raw.main,DictionaryFactory.getMainDictionaryResourceId(
                c.getResources(),new Locale("zz")));
    }
    @Test public void languageNamesResolveUnderTheInstalledAppIdentity() {
        Context c=RuntimeEnvironment.getApplication();SubtypeLocale.init(c);
        assertTrue(SubtypeLocale.getSubtypeNameId("en_US","qwerty")!=0);
        assertTrue(SubtypeLocale.getSubtypeNameId("zz","qwerty")!=0);
    }
}
