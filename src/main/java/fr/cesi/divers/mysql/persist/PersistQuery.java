package fr.cesi.divers.mysql.persist;

import fr.cesi.divers.mysql.connector.SQLConnectionAdapter;
import fr.cesi.divers.mysql.connector.SQLConnectionAdapterFactory;
import fr.cesi.divers.mysql.persist.annotation.Key;
import fr.cesi.divers.mysql.utils.ReflectionUtils;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.util.*;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class PersistQuery<T extends Persist> {
    private final Class<? extends Persist> clazz;

    private String query = "";
    private Object[] objectsForStatement = new Object[0];

    public PersistQuery<T> select(Persist persist) {
        HashMap<String, Object> persistData = getPersistData(persist);

        return select(persistData.keySet().toArray(new String[0]))
                .where(persistData);
    }

    public PersistQuery<T> select(String... fields) {
        String table = Persist.getTable(clazz);
        query += String.format(
                "SELECT `%s` FROM `%s` ",
                String.join("`,`", fields),
                Persist.getTable(clazz)
        );
        query = query.replaceAll("`\\*`", "*");
        return this;
    }

    public PersistQuery<T> update(Persist persist) {
        HashMap<String, Object> persistData = getPersistData(persist);

        return update(persistData).where(new HashMap<String, Object>() {{
            put("id", persist.getId());
        }});
    }

    public PersistQuery<T> update(Map<String, Object> fields) {
        String table = Persist.getTable(clazz);

        query += String.format(
                "UPDATE `%s` SET `%s`=? ",
                table,
                String.join("`=?, `", fields.keySet())
        );

        ArrayList<Object> objects = new ArrayList<>(Arrays.asList(objectsForStatement));
        objects.addAll(fields.values());
        objectsForStatement = objects.toArray(new Object[0]);

        return this;
    }

    public PersistQuery<T> delete() {
        String table = Persist.getTable(clazz);

        query += String.format(
                "DELETE FROM `%s` ",
                table
        );

        return this;
    }

    public PersistQuery<T> insert(Persist persist) {
        return insert(getPersistData(persist));
    }

    public PersistQuery<T> insert(Map<String, Object> fields) {
        String table = Persist.getTable(clazz);
        query += String.format(
                "INSERT INTO `%s` (`%s`) VALUES (%s)",
                table,
                String.join("`, `", fields.keySet()),
                fields.values().stream().map(o -> "?").collect(Collectors.joining(", "))
        );

        ArrayList<Object> objects = new ArrayList<>(Arrays.asList(objectsForStatement));
        objects.addAll(fields.values());
        objectsForStatement = objects.toArray(new Object[0]);

        return this;
    }

    public PersistQuery<T> where(HashMap<String, Object> where) {
        query += "WHERE ";

        for (String s : where.keySet())
            query += "`" + s + "`=? AND ";


        ArrayList<Object> objects = new ArrayList<>(Arrays.asList(objectsForStatement));
        objects.addAll(where.values());
        objectsForStatement = objects.toArray(new Object[0]);

        query = query.substring(0, query.length()-"AND ".length());
        return this;
    }

    public PersistQuery<T> orderBy(String column, String order) {
        query += "ORDER BY `" + column + "` " + order + " ";
        return this;
    }

    public PersistQuery<T> limit(int limit) {
        query += "LIMIT " + limit + " ";
        return this;
    }

    @SneakyThrows
    public List<T> executeQuery() {
        List<T> persists = new ArrayList<>();
        Optional<SQLConnectionAdapter> optionalSQLConnectionAdapter = SQLConnectionAdapterFactory
                .getInstance()
                .getSQLConnectionAdapter(clazz);

        if (optionalSQLConnectionAdapter.isPresent()) {
            SQLConnectionAdapter sqlConnectionAdapter = optionalSQLConnectionAdapter.get();

            Optional<ResultSet> optionalResultSet = sqlConnectionAdapter.query(query, objectsForStatement);

            if (optionalResultSet.isPresent()) {
                ResultSet resultSet = optionalResultSet.get();
                while (resultSet.next()) {
                    T persistType = (T) clazz.newInstance().parseFromResultSet(resultSet);
                    persists.add(persistType);
                }
            }
        }

        return persists;
    }

    public int executeUpdate() {
        Optional<SQLConnectionAdapter> sqlConnectionAdapter = SQLConnectionAdapterFactory
                .getInstance()
                .getSQLConnectionAdapter(clazz);

        return sqlConnectionAdapter.map(connectionAdapter -> connectionAdapter.update(query, objectsForStatement)).orElse(0);
    }

    public int createTable() {
        Optional<SQLConnectionAdapter> sqlConnectionAdapter = SQLConnectionAdapterFactory
                .getInstance().getSQLConnectionAdapter(clazz);
        StringBuilder createTableQuery = new StringBuilder(
                String.format("CREATE TABLE IF NOT EXISTS `%s` (", Persist.getTable(clazz))
        );

        List<Field> fields = ReflectionUtils.getDeclaredFields(clazz);
        fields = fields.stream().filter(field -> field.getAnnotation(Key.class) != null).collect(Collectors.toList());

        for (Field field : fields) {
            Key key = field.getAnnotation(Key.class);
            String fieldQuery = "`" + field.getName() + "` " + key.keyType().name().toLowerCase();

            if (key.length() != -1)
                fieldQuery += "(" + key.length() + ")";

            if (key.unsigned())
                fieldQuery += " UNSIGNED";

            if (key.notNull())
                fieldQuery += " NOT NULL";

            if (key.autoincrement())
                fieldQuery += " AUTO_INCREMENT";

            fieldQuery += ", ";
            createTableQuery.append(fieldQuery);

            if (key.autoincrement())
                createTableQuery.append("KEY(").append(field.getName()).append("), ");
        }

        createTableQuery
                .replace(createTableQuery.length()-", ".length(), createTableQuery.length(), "")
                .append(");");

        return sqlConnectionAdapter.map(connectionAdapter -> connectionAdapter.update(createTableQuery.toString()))
                .orElse(0);
    }

    @SneakyThrows
    private HashMap<String, Object> getPersistData(Persist persist) {
        final HashMap<String, Object> persistData = new HashMap<>();
        Class<?>[] classes = new Class<?>[] {
                clazz.getSuperclass(),
                clazz
        };

        for (Class<?> aClass : classes) {
            for (Field declaredField : aClass.getDeclaredFields()) {
                declaredField.setAccessible(true);
                persistData.put(declaredField.getName(), declaredField.get(persist));
            }
        }

        return persistData;
    }
}
