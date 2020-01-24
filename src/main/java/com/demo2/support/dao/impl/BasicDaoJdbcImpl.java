/* 
 * Created by 2019年4月17日
 */
package com.demo2.support.dao.impl;

import java.io.Serializable;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;

import com.demo2.support.dao.BasicDao;
import com.demo2.support.dao.impl.factory.Property;
import com.demo2.support.dao.impl.factory.VObj;
import com.demo2.support.dao.impl.factory.VObjFactory;
import com.demo2.support.dao.impl.mybatis.GenericDao;
import com.demo2.support.entity.Entity;
import com.demo2.support.exception.DaoException;
import com.demo2.support.utils.BeanUtils;
import com.demo2.support.utils.DateUtils;

/**
 * The implement of BasicDao with Jdbc.
 * @author fangang
 */
public class BasicDaoJdbcImpl implements BasicDao {
	@Autowired
	private GenericDao dao;

	@Override
	public <T> void insert(T vo) {
		if(vo==null) throw new DaoException("The value object is null");
		TmpObj tmpObj = readDataFromVo(vo);
		try {
			dao.insert(tmpObj.tableName, tmpObj.columns, tmpObj.values);
		} catch (DataAccessException e) {
			throw new DaoException("error when insert vo");
		}
	}

	@Override
	public <T> void update(T vo) {
		if(vo==null) throw new DaoException("The value object is null");
		TmpObj tmpObj = readDataFromVo(vo);
		try {
			dao.update(tmpObj.tableName, tmpObj.colMap, tmpObj.pkMap);
		} catch (DataAccessException e) {
			throw new DaoException("error when update vo");
		}
	}

	@Override
	public <T> void insertOrUpdate(T vo) {
		if(vo==null) throw new DaoException("The value object is null");
		TmpObj tmpObj = readDataFromVo(vo);
		try {
			dao.insert(tmpObj.tableName, tmpObj.columns, tmpObj.values);
		} catch (DataAccessException e) {
			if(e.getCause() instanceof SQLIntegrityConstraintViolationException)
				update(vo);
			else throw new DaoException("error when insert vo");
		}
	}

	@Override
	public <T, S extends Collection<T>> void insertOrUpdateForList(S list) {
		for(Object vo : list) insertOrUpdate(vo);
	}

	@Override
	public <T> void delete(T vo) {
		TmpObj tmpObj = readDataFromVo(vo);
		dao.delete(tmpObj.tableName, tmpObj.pkMap);
	}

	@Override
	public <T, S extends Collection<T>> void deleteForList(S list) {
		for(Object vo : list) delete(vo);
	}

	@Override
	public <S extends Serializable, T extends Entity<S>> void deleteForList(List<S> ids, T template) {
		TmpObj tmpObj = prepareForList(ids, template);
		dao.deleteForList(tmpObj.tableName, tmpObj.pkMap);
	}
	
	@Override
	public <S extends Serializable, T extends Entity<S>> T load(S id, T template) {
		if(id==null||template==null) throw new DaoException("illegal parameters!");
		template.setId(id);
		TmpObj tmpObj = readDataFromVo(template);
		List<Map<String, Object>> list = dao.find(tmpObj.tableName, tmpObj.pkMap);
		if(list.isEmpty()) return null;
		Map<String, Object> map = list.get(0);
		return this.setMapToVo(map, template);
	}
	
	@Override
	public <S extends Serializable, T extends Entity<S>> List<T> loadForList(List<S> ids, T template) {
		TmpObj tmpObj = prepareForList(ids, template);
		
		List<Map<String, Object>> list = dao.load(tmpObj.tableName, tmpObj.pkMap);
		
		//convert result set from List<Map> to List<Entity>
		List<T> listOfVo = new ArrayList<T>();
		for(Map<String, Object> map : list) {
			@SuppressWarnings("unchecked")
			T temp = (T)BeanUtils.createEntity((Class<T>)template.getClass());
			T vo = this.setMapToVo(map, temp);
			listOfVo.add(vo);
		}
		return listOfVo;
	}
	
	private <S extends Serializable, T extends Entity<S>> TmpObj prepareForList(List<S> ids, T template) {
		if(template==null) throw new DaoException("illegal parameters!");
		if(ids==null||ids.isEmpty()) return null;
		
		//list of TmpObj, which help to execute sql.
		List<TmpObj> listOfTmpObj = new ArrayList<>();
		for(S id : ids) {
			@SuppressWarnings("unchecked")
			T temp = (T)BeanUtils.createEntity((Class<T>)template.getClass());
			temp.setId(id);
			TmpObj tmpObj = readDataFromVo(temp);
			listOfTmpObj.add(tmpObj);
		}
		
		//
		Map<Object, List<Object>> mapOfValues = new HashMap<>();
		for(TmpObj tmpObj : listOfTmpObj) {
			 for(Map<Object, Object> map : tmpObj.pkMap) {
				 Object key = map.get("key");
				 Object value = map.get("value");
				 if(mapOfValues.get(key)==null) mapOfValues.put(key, new ArrayList<Object>());
				 mapOfValues.get(key).add(value);
			 }
		}
		
		TmpObj tmpObj = readDataFromVo(template);
		List<Map<Object, Object>> pkMap = new ArrayList<>();
		for(Object key : mapOfValues.keySet()) {
			Map<Object, Object> map = new HashMap<>();
			map.put("key", key);
			map.put("value", mapOfValues.get(key));
			pkMap.add(map);
		}
		tmpObj.pkMap = pkMap;
		return tmpObj;
	}
	
	@Override
	public <S extends Serializable, T extends Entity<S>> List<T> loadAll(T template) {
		TmpObj tmpObj = readDataFromVo(template);
		List<Map<String, Object>> list = dao.find(tmpObj.tableName, tmpObj.colMap);
		
		List<T> listOfVo = new ArrayList<>();
		for(Map<String, Object> map : list) {
			@SuppressWarnings("unchecked")
			T temp = (T)BeanUtils.createEntity((Class<T>)template.getClass());
			T vo = this.setMapToVo(map, temp);
			listOfVo.add(vo);
		}
		return listOfVo;
	}
	
	@Override
	public <S extends Serializable, T extends Entity<S>> void delete(S id, T template) {
		if(id==null||template==null) throw new DaoException("illegal parameters!");
		T vo = this.load(id, template);
		this.delete(vo);
	}
	
	/**
	 * according to the configure, read each of field's value from the value object.
	 * @param vo the value object
	 * @return the result object
	 */
	private TmpObj readDataFromVo(Object vo) {
		if(vo==null) throw new DaoException("The value object is null");
		
		VObj vObj = VObjFactory.getVObj(vo.getClass().getName());
		if(vObj==null) throw new DaoException("No found the entity ["+vo.getClass().getName()+"] in the vObj.xml");
		
		List<Property> properties = vObj.getProperties();
		TmpObj tmpObj = new TmpObj();
		tmpObj.tableName = vObj.getTable();
		
		for(Property property : properties) {
			String name = property.getName();
			String column = property.getColumn();
			Object value = BeanUtils.getValueByField(vo, name);
			
			if(value==null) continue;
			tmpObj.columns.add(column);
			tmpObj.values.add(value);
			
			Map<Object, Object> map = new HashMap<>();
			map.put("key", column);
			map.put("value", value);
			tmpObj.colMap.add(map);
			
			if(property.isPrimaryKey()) tmpObj.pkMap.add(map);
		}
		return tmpObj;
	}
	
	/**
	 * @param map
	 * @param vo
	 * @return
	 */
	private <T> T setMapToVo(Map<String, Object> map, T vo) {
		if(map==null && vo==null) throw new DaoException("Illegal parameters!");
		for(String key : map.keySet()) {
			Object value = map.get(key);
			BeanUtils.setValueByField(vo, key, new BeanUtils.BeanCallback() {
				@Override
				public Object getValue(Class<?> clazz) {
					return bind(clazz, value);
				}
			});
		}
		return vo;
	}
	
	/**
	 * Downcast the value to the class it is.
	 * @param clazz
	 * @param value
	 * @return the downcast value
	 */
	private Object bind(Class<?> clazz, Object value) {
		if(value==null) return value;
		if(clazz.equals(String.class)) return value;
		
		String str = value.toString();
		if(clazz.equals(Long.class)||clazz.equals(long.class)) return new Long(str);
		if(clazz.equals(Integer.class)||clazz.equals(int.class)) return new Integer(str);
		if(clazz.equals(Double.class)||clazz.equals(double.class)) return new Double(str);
		if(clazz.equals(Float.class)||clazz.equals(float.class)) return new Float(str);
		if(clazz.equals(Short.class)||clazz.equals(short.class)) return new Short(str);
		
		if(clazz.equals(Date.class)&&str.length()==10) return DateUtils.getDate(str,"yyyy-MM-dd");
		if(clazz.equals(Date.class)) return DateUtils.getDate(str,"yyyy-MM-dd HH:mm:ss");
		
		//TODO how to bind map, list and set.
		return value;
	}
	
	class TmpObj {
		String tableName;
		List<Object> columns = new ArrayList<>();
		List<Object> values = new ArrayList<>();
		List<Map<Object, Object>> colMap = new ArrayList<>();
		List<Map<Object, Object>> pkMap = new ArrayList<>();
	}
}
