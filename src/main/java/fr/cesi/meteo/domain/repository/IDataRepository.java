package fr.cesi.meteo.domain.repository;

import fr.cesi.meteo.domain.model.Data;

public interface IDataRepository {

    Data[] findLasts(int lasts);

}
