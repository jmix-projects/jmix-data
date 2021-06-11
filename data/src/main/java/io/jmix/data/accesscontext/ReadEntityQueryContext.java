package io.jmix.data.accesscontext;


import com.google.common.base.Strings;
import io.jmix.core.Metadata;
import io.jmix.data.QueryParser;
import io.jmix.data.QueryTransformer;
import io.jmix.data.QueryTransformerFactory;
import io.jmix.core.accesscontext.AccessContext;
import io.jmix.core.common.util.StringHelper;
import io.jmix.core.metamodel.model.MetaClass;
import io.jmix.data.JmixQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Modifies the query depending on current security constraints.
 */
public class ReadEntityQueryContext implements AccessContext {

    protected final QueryTransformerFactory queryTransformerFactory;

    @SuppressWarnings("rawtypes")
    protected final JmixQuery originalQuery;
    protected final MetaClass entityClass;
    protected final boolean singleResult;
    protected List<Condition> conditions;
    protected Function<String, Object> queryParamsProvider;

    private static final Logger log = LoggerFactory.getLogger(ReadEntityQueryContext.class);

    protected static class Condition {
        final String join;
        final String where;

        public Condition(String join, String where) {
            this.join = join;
            this.where = where;
        }
    }

    public ReadEntityQueryContext(@SuppressWarnings("rawtypes") JmixQuery originalQuery,
                                  MetaClass entityClass,
                                  QueryTransformerFactory transformerFactory) {
        this.originalQuery = originalQuery;
        this.entityClass = entityClass;
        this.queryTransformerFactory = transformerFactory;
        this.singleResult = false;
    }

    public ReadEntityQueryContext(@SuppressWarnings("rawtypes") JmixQuery originalQuery,
                                  QueryTransformerFactory transformerFactory,
                                  Metadata metadata) {
        this.originalQuery = originalQuery;
        this.queryTransformerFactory = transformerFactory;
        this.singleResult = false;

        QueryParser parser = transformerFactory.parser(originalQuery.getQueryString());
        this.entityClass = metadata.getClass(parser.getEntityName());
    }

    public MetaClass getEntityClass() {
        return entityClass;
    }

    public void addJoinAndWhere(String join, String where) {
        if (conditions == null) {
            conditions = new ArrayList<>();
        }
        conditions.add(new Condition(join, where));
    }

    public void setQueryParamsProvider(Function<String, Object> queryParamsProvider) {
        this.queryParamsProvider = queryParamsProvider;
    }

    @SuppressWarnings("rawtypes")
    public JmixQuery getResultQuery() {
        buildQuery();
        return originalQuery;
    }

    protected void buildQuery() {
        if (conditions != null) {
            QueryTransformer transformer = queryTransformerFactory.transformer(originalQuery.getQueryString());
            boolean hasJoins = false;

            for (Condition condition : conditions) {
                try {
                    if (!Strings.isNullOrEmpty(condition.join)) {
                        hasJoins = true;
                        transformer.addJoinAndWhere(condition.join, condition.where);
                    } else {
                        transformer.addWhere(condition.where);
                    }
                } catch (Exception e) {
                    log.error("An error occurred when applying row level for entity {}. Join clause {}, where clause {}",
                            entityClass.getName(), condition.join, condition.where, e);

                    throw new RuntimeException(
                            String.format("An error occurred when applying row level for entity %s", entityClass.getName()));
                }
            }

            if (hasJoins && singleResult) {
                transformer.addDistinct();
            }
            originalQuery.setQueryString(transformer.getResult());

            if (queryParamsProvider != null) {
                for (String param : transformer.getAddedParams()) {
                    originalQuery.setParameter(param, queryParamsProvider.apply(param));
                }
            }

            if (log.isTraceEnabled()) {
                log.trace("Query with row-level constraints applied: {}", printQuery(originalQuery.getQueryString()));
            }
        }
    }

    protected static String printQuery(String query) {
        return query == null ? null : StringHelper.removeExtraSpaces(query.replace('\n', ' '));
    }
}
