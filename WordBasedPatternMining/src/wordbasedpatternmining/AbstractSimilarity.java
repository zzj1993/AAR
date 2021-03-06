/**
 * From http://sujitpal.blogspot.com.au/2008/09/ir-math-with-java-similarity-measures.html
 */
package wordbasedpatternmining;

import org.apache.commons.collections15.Transformer;
import Jama.Matrix;

/**
 * Abstract class for calculating similarity between Matrix objects.<p>
 * 
 * Transforms matrices and computes the similarity between them. 
 * @author Jordan
 */
public abstract class AbstractSimilarity implements Transformer<Matrix, Matrix> {

    @Override
    public Matrix transform(Matrix termDocumentMatrix) {
        int numDocs = termDocumentMatrix.getColumnDimension();
        Matrix similarityMatrix = new Matrix(numDocs, numDocs);
        for (int i = 0; i < numDocs; i++) {
            Matrix sourceDocMatrix = termDocumentMatrix.getMatrix(
                    0, termDocumentMatrix.getRowDimension() - 1, i, i);
            for (int j = 0; j < numDocs; j++) {
                Matrix targetDocMatrix = termDocumentMatrix.getMatrix(
                        0, termDocumentMatrix.getRowDimension() - 1, j, j);
                similarityMatrix.set(i, j,
                        computeSimilarity(sourceDocMatrix, targetDocMatrix));
            }
        }
        return similarityMatrix;
    }

    /**
     * Given two matrices, calculate the cosine similarity of the objects relative to each other.
     * @param sourceDoc A matrix object
     * @param targetDoc A comparison matrix object
     * @return Similarity calculation
     */
    protected abstract double computeSimilarity(Matrix sourceDoc, Matrix targetDoc);
}
