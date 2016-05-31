package com.redhat.lightblue.assoc.ep;

import java.util.List;
import java.util.ArrayList;

import com.redhat.lightblue.metadata.CompositeMetadata;
import com.redhat.lightblue.metadata.ResolvedReferenceField;

import com.redhat.lightblue.assoc.BoundObject;
import com.redhat.lightblue.assoc.Conjunct;
import com.redhat.lightblue.assoc.RewriteQuery;

import com.redhat.lightblue.query.QueryExpression;

/**
 * Keeps an edge query along with its binding information.
 */
public class AssociationQuery {

    private final List<BoundObject> fieldBindings = new ArrayList<>();
    private final QueryExpression query;
    private final ResolvedReferenceField reference;

    public AssociationQuery(CompositeMetadata root,
                            CompositeMetadata currentEntity,
                            ResolvedReferenceField reference,
                            List<Conjunct> conjuncts) {
        this.reference = reference;
        RewriteQuery rewriter = new RewriteQuery(root, currentEntity);
        List<QueryExpression> queries = new ArrayList<>(conjuncts.size());
        for (Conjunct c : conjuncts) {
            RewriteQuery.RewriteQueryResult result = rewriter.rewriteQuery(c.getClause(), c.getFieldInfo());
            queries.add(result.query);
            fieldBindings.addAll(result.bindings);
        }
        query = Searches.and(queries);
    }

    public QueryExpression getQuery() {
        return query;
    }

    public List<BoundObject> getFieldBindings() {
        return fieldBindings;
    }

    /**
     * Returns the reference field from the parent entity to current entity
     */
    public ResolvedReferenceField getReference() {
        return reference;
    }

    @Override
    public String toString() {
        return query.toString() + " [ " + fieldBindings + " ]";
    }
}
