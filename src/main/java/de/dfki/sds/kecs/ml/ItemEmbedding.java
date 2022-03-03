package de.dfki.sds.kecs.ml;

import de.dfki.sds.stringanalyzer.string.StringEntity;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

/**
 * From item to vector which can be used in the provider and predictor.
 *
 * 
 */
public abstract class ItemEmbedding {

    public abstract List<String> getFeatures();

    public abstract void add(StringEntity item);

    public abstract void update();

    public abstract double[] getVector(StringEntity item);

    public String getFeatureValues(StringEntity item) {
        StringJoiner sj = new StringJoiner(", ");
        double[] vector = getVector(item);
        if (vector != null) {
            for (int i = 0; i < vector.length; i++) {
                if (vector[i] > 0) {
                    String line = getFeatures().get(i) + " (" + String.format(Locale.ENGLISH, "%.2f", vector[i]) + ")";
                    sj.add(line);
                }
            }
        }
        return sj.toString();
    }
}
