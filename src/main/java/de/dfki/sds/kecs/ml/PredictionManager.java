package de.dfki.sds.kecs.ml;

import de.dfki.sds.hephaistos.storage.assertion.Assertion;
import de.dfki.sds.hephaistos.storage.assertion.AssertionPool;
import de.dfki.sds.hephaistos.storage.file.FileInfoStorage;
import de.dfki.sds.kecs.util.ExceptionUtility;
import de.dfki.sds.kecs.util.Prediction;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import weka.classifiers.AbstractClassifier;
import weka.classifiers.Evaluation;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.functions.Logistic;
import weka.classifiers.functions.SMO;
import weka.classifiers.lazy.IBk;
import weka.classifiers.trees.J48;
import weka.classifiers.trees.RandomForest;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;

/**
 * A manager that provides a classifier and is configurable for each module.
 */
public class PredictionManager {

    private AbstractClassifier classifier;
    private List<String> classLabels;
    private Instances dataset;

    private double confidenceThreshold = 0.1;

    private Consumer<List<Attribute>> schemaDefinition;
    //put assertion to instance
    private Consumer<Context> featureDefinition;
    //for an assertion provides the class label
    private Function<Assertion, String> classProvider;
    //contains assertion and predicted class
    @Deprecated
    private Consumer<Context> predictionConsumer;
    //contains all predictions
    private Consumer<Context> predictionsConsumer;

    //get all assertions that are used for training
    private Consumer<Context> trainSetProvider;
    //get all assertions for that we want to predict a class
    private Consumer<Context> testSetProvider;

    public static final String AVOID_UNARY_CLASS = "urn:ml:avoidUnaryClass";

    private boolean printDataset;
    private boolean printEvaluation;
    private boolean printClassifier;

    public PredictionManager() {

    }

    public void svm() {
        classifier = new SMO();
        dataset = null;
    }

    public void knn(int k) {
        classifier = new IBk();
        try {
            classifier.setOptions(new String[]{"-K", String.valueOf(k)});
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        dataset = null;
    }

    public void j48() {
        classifier = new J48();
        /*
        try {
            classifier.setOptions(new String[] { "-C", "0.1", "-M", "1" });
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
         */
        dataset = null;
    }

    public void randomForest() {
        classifier = new RandomForest();
        dataset = null;
    }

    public void naiveBayes() {
        classifier = new NaiveBayes();
        dataset = null;
    }

    public void logistic() {
        classifier = new Logistic();
        dataset = null;
    }

    public void train(FileInfoStorage fileInfoStorage, AssertionPool pool) {
        dataset = null;
        classLabels = null;

        Context ctx = new Context();
        ctx.fileInfoStorage = fileInfoStorage;
        ctx.assertionPool = pool;
        ctx.assertionSet = new HashSet<>();

        trainSetProvider.accept(ctx);

        //all class labels = class uris
        Set<String> classes = new HashSet<>();
        for (Assertion assertion : ctx.assertionSet) {
            classes.add(classProvider.apply(assertion));
        }

        //avoid unary class
        if (classes.size() == 1) {
            classes.add(AVOID_UNARY_CLASS);
        }

        classLabels = new ArrayList<>(classes);
        classLabels.sort((a, b) -> a.compareTo(b));

        //create dataset
        ArrayList<Attribute> attrList = new ArrayList<>();
        schemaDefinition.accept(attrList);
        attrList.add(new Attribute("class", classLabels));
        dataset = new Instances("tmp", attrList, 0);
        dataset.setClassIndex(dataset.numAttributes() - 1);

        //create instances
        for (Assertion assertion : ctx.assertionSet) {

            Instance inst = toInstance(
                    assertion,
                    classProvider.apply(assertion),
                    fileInfoStorage, pool);

            dataset.add(inst);
        }

        //to avoid Cannot handle multi-valued nominal class!
        if(dataset.isEmpty()) {
            dataset = null;
            return;
        }
        
        if (classes.size() == 1) {
            //avoid unary class
            Instance inst = new DenseInstance(dataset.numAttributes());
            inst.setDataset(dataset);
            inst.setValue(dataset.numAttributes() - 1, AVOID_UNARY_CLASS);
            dataset.add(inst);
        }

        try {
            classifier.buildClassifier(dataset);
        } catch (weka.core.UnsupportedAttributeTypeException ex) {
            ExceptionUtility.save(ex);
            //ignore if "Cannot handle unary class!"
            System.out.println("[Prediction Manager Warning] " + ex.getMessage());
            System.out.println(dataset);
            dataset = null;
            return;
        } catch (Exception ex) {
            ExceptionUtility.save(ex);
            throw new RuntimeException(ex);
        }

        if (printDataset) {
            System.out.println(dataset);
        }

        if (printClassifier) {
            System.out.println(classifier);
        }

        if (printEvaluation) {
            try {
                Evaluation eval = new Evaluation(dataset);
                eval.evaluateModel(classifier, dataset);
                System.out.println(eval.toSummaryString());
            } catch (Exception ex) {
                ExceptionUtility.save(ex);
                throw new RuntimeException(ex);
            }
        }
    }

    public void predict(FileInfoStorage fileInfoStorage, AssertionPool pool) {
        if (!classifierIsTrained()) {
            return;
        }

        Context ctx = new Context();
        ctx.fileInfoStorage = fileInfoStorage;
        ctx.assertionPool = pool;
        ctx.assertionSet = new HashSet<>();
        testSetProvider.accept(ctx);

        //collect all predictions
        List<Prediction> predictions = new ArrayList<>();

        //test set
        for (Assertion assertion : ctx.assertionSet) {

            //we need an instance from the concept
            Instance inst = toInstance(assertion, null, fileInfoStorage, pool);

            try {
                classifier.classifyInstance(inst);

                double[] dArray = classifier.distributionForInstance(inst);
                for (int i = 0; i < dArray.length; i++) {

                    double conf = dArray[i];
                    String classLbl = classLabels.get(i);

                    if (classLbl.equals(AVOID_UNARY_CLASS)) {
                        conf = 0;
                    }

                    Prediction prediction = new Prediction(classLbl, conf);
                    prediction.setAssertion(assertion);
                    prediction.setInstance(inst);
                    predictions.add(prediction);
                }
            } catch (Exception ex) {
                ExceptionUtility.save(ex);
                throw new RuntimeException(ex);
            }
        }

        //cut and sort
        predictions.removeIf(p -> p.getConfidence() < confidenceThreshold);
        predictions.sort((a, b) -> Double.compare(b.getConfidence(), a.getConfidence()));

        //pass predictions to module
        Context predCtx = new Context();
        predCtx.fileInfoStorage = fileInfoStorage;
        predCtx.assertionPool = pool;
        predCtx.predictions = predictions;

        predictionsConsumer.accept(predCtx);

        //we commit what is predicted by AI
        pool.commit();
    }

    public void trainAndPredict(FileInfoStorage fileInfoStorage, AssertionPool pool) {
        train(fileInfoStorage, pool);
        predict(fileInfoStorage, pool);
    }

    private Instance toInstance(Assertion assertion, String classLabel, FileInfoStorage fileInfoStorage, AssertionPool pool) {
        Instance inst = new DenseInstance(dataset.numAttributes());
        inst.setDataset(dataset);

        //features(inst, conceptResource, fileInfoStorage, pool);
        Context ctx = new Context();
        ctx.assertion = assertion;
        ctx.instance = inst;
        ctx.fileInfoStorage = fileInfoStorage;
        ctx.assertionPool = pool;

        featureDefinition.accept(ctx);

        //class
        if (classLabel != null) {
            inst.setValue(dataset.numAttributes() - 1, classLabel);
        }

        return inst;
    }

    private boolean classifierIsTrained() {
        return dataset != null;
    }

    //getter & setter
    public double getConfidenceThreshold() {
        return confidenceThreshold;
    }

    public void setConfidenceThreshold(double confidenceThreshold) {
        this.confidenceThreshold = confidenceThreshold;
    }

    public Consumer<List<Attribute>> getSchemaDefinition() {
        return schemaDefinition;
    }

    public void setSchemaDefinition(Consumer<List<Attribute>> schemaDefinition) {
        this.schemaDefinition = schemaDefinition;
    }

    public Consumer<Context> getFeatureDefinition() {
        return featureDefinition;
    }

    public void setFeatureDefinition(Consumer<Context> featureDefinition) {
        this.featureDefinition = featureDefinition;
    }

    public Consumer<Context> getTrainSetProvider() {
        return trainSetProvider;
    }

    public void setTrainSetProvider(Consumer<Context> trainSetProvider) {
        this.trainSetProvider = trainSetProvider;
    }

    public Consumer<Context> getTestSetProvider() {
        return testSetProvider;
    }

    public void setTestSetProvider(Consumer<Context> testSetProvider) {
        this.testSetProvider = testSetProvider;
    }

    public Function<Assertion, String> getClassProvider() {
        return classProvider;
    }

    public void setClassProvider(Function<Assertion, String> classProvider) {
        this.classProvider = classProvider;
    }

    @Deprecated
    public Consumer<Context> getPredictionConsumer() {
        return predictionConsumer;
    }

    @Deprecated
    public void setPredictionConsumer(Consumer<Context> predictionConsumer) {
        this.predictionConsumer = predictionConsumer;
    }

    public Consumer<Context> getPredictionsConsumer() {
        return predictionsConsumer;
    }

    public void setPredictionsConsumer(Consumer<Context> predictionsConsumer) {
        this.predictionsConsumer = predictionsConsumer;
    }

    public boolean isPrintDataset() {
        return printDataset;
    }

    public void setPrintDataset(boolean printDataset) {
        this.printDataset = printDataset;
    }

    public boolean isPrintEvaluation() {
        return printEvaluation;
    }

    public void setPrintEvaluation(boolean printEvaluation) {
        this.printEvaluation = printEvaluation;
    }

    public boolean isPrintClassifier() {
        return printClassifier;
    }

    public void setPrintClassifier(boolean printClassifier) {
        this.printClassifier = printClassifier;
    }

    //---------------------------------------------------
    public class Context {

        private FileInfoStorage fileInfoStorage;
        private AssertionPool assertionPool;

        //for features: an assertion is transformed to an instance
        private Assertion assertion;
        private Instance instance;

        private Set<Assertion> assertionSet;

        @Deprecated
        private Prediction prediction;

        private List<Prediction> predictions;

        public Context() {
        }

        public Context(FileInfoStorage fileInfoStorage, AssertionPool assertionPool) {
            this.fileInfoStorage = fileInfoStorage;
            this.assertionPool = assertionPool;
        }

        public Context(FileInfoStorage fileInfoStorage, AssertionPool assertionPool, Assertion assertion, Instance instance) {
            this.fileInfoStorage = fileInfoStorage;
            this.assertionPool = assertionPool;
            this.assertion = assertion;
            this.instance = instance;
        }

        public FileInfoStorage getFileInfoStorage() {
            return fileInfoStorage;
        }

        public AssertionPool getAssertionPool() {
            return assertionPool;
        }

        public Assertion getAssertion() {
            return assertion;
        }

        public Instance getInstance() {
            return instance;
        }

        public Set<Assertion> getAssertionSet() {
            return assertionSet;
        }

        @Deprecated
        public Prediction getPrediction() {
            return prediction;
        }

        public List<Prediction> getPredictions() {
            return predictions;
        }

    }

}
