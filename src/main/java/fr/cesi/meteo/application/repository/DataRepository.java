package fr.cesi.meteo.application.repository;

import fr.cesi.meteo.domain.entity.Data;
import fr.cesi.divers.mysql.persist.PersistQuery;
import fr.cesi.meteo.domain.repository.IDataRepository;

import java.math.BigInteger;
import java.util.HashMap;

public class DataRepository implements IDataRepository {

    @Override
    public Data[] findLasts(int lasts) {
        return new PersistQuery<>(Data.class)
                .select("*")
                .orderBy("id DESC")
                .limit(lasts)
                .executeQuery()
                .toArray(new Data[0]);
    }

    @Override
    public int create(Data data) {
        return new PersistQuery<>(Data.class)
                .insert(data)
                .executeUpdate();
    }

    @Override
    public Data find(int id) {
        return (Data) new PersistQuery<>(Data.class)
                .select("*")
                .where(new HashMap<String, Object>() {{
                    put("id", id);
                }})
                .executeQuery()
                .get(0);
    }
}
