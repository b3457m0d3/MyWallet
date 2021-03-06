/*
MyWallet is an android application which helps users to manager their personal accounts.
Copyright (C) 2012 Santiago Munin

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.   
 */
package es.udc.santiago.model.facade;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.LinkedList;
import java.util.List;

import android.util.Log;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.Where;

import es.udc.santiago.model.backend.CashFlowVO;
import es.udc.santiago.model.backend.DatabaseHelper;
import es.udc.santiago.model.exceptions.DuplicateEntryException;
import es.udc.santiago.model.exceptions.EntryNotFoundException;
import es.udc.santiago.model.util.GenericService;
import es.udc.santiago.model.util.ModelUtilities;

/**
 * Specifies methods which process cashflows.
 * 
 * @author Santiago Munín González
 * 
 */
public class CashFlowService implements GenericService<Long, CashFlow> {

	private static final String TAG = "CashFlowService";
	private Dao<CashFlowVO, Long> cashDao;

	public CashFlowService(DatabaseHelper dbHelper) throws SQLException {
		cashDao = dbHelper.getCashFlowDao();
	}

	public Long add(CashFlow object) {
		Log.i(TAG, "Adding...");
		CashFlowVO cashFlowValueObj = ModelUtilities
				.cashFlowPublicObjToValueObj(object);
		if (cashFlowValueObj == null) {
			return (long) -1;
		}
		try {
			this.cashDao.create(cashFlowValueObj);
			return cashFlowValueObj.getId();
		} catch (SQLException e) {
			// TODO improve
			throw new RuntimeException();
		}
	}

	@Override
	public CashFlow get(Long key) throws EntryNotFoundException {
		Log.i(TAG, "Getting...");
		CashFlowVO fetched;
		try {
			fetched = this.cashDao.queryForId(key);
			this.cashDao.refresh(fetched);
		} catch (SQLException e) {
			throw new EntryNotFoundException();
		}
		return ModelUtilities.cashFlowValueObjToPublicObj(fetched);
	}

	@Override
	public List<CashFlow> getAll() {
		Log.i(TAG, "Getting all...");
		List<CashFlowVO> list;
		try {
			list = this.cashDao.queryForAll();
			List<CashFlow> res = new ArrayList<CashFlow>();
			for (CashFlowVO cashFlowVO : list) {
				res.add(ModelUtilities.cashFlowValueObjToPublicObj(cashFlowVO));
			}
			return res;
		} catch (SQLException e) {
			Log.e(TAG, e.getMessage());
			throw new RuntimeException();
		}
	}

	@Override
	public void update(CashFlow object) throws EntryNotFoundException,
			DuplicateEntryException {
		Log.i(TAG, "Updating...");
		CashFlowVO updateObject = ModelUtilities
				.cashFlowPublicObjToValueObj(object);
		if (updateObject != null) {
			try {
				this.cashDao.update(updateObject);
			} catch (SQLException e) {
				throw new EntryNotFoundException();
			}
		}
	}

	@Override
	public void delete(Long key) throws EntryNotFoundException {
		Log.i(TAG, "Deleting...");
		try {
			this.cashDao.deleteById(key);
		} catch (SQLException e) {
			throw new EntryNotFoundException();
		}
	}

	@Override
	public boolean exists(CashFlow object) {
		Log.i(TAG, "Checking if exists...");
		try {
			return this.cashDao.idExists(object.getId());
		} catch (SQLException e) {
			return false;
		}
	}

	//TODO improve the code below
	/**
	 * Fetches cashflows from DB, setting the filters from arguments. Set null
	 * if you want avoid it.
	 * 
	 * @param start
	 *            Start day, required.
	 * 
	 * @param period
	 *            Period (daily, monthly or yearly), required.
	 * @param type
	 *            CashFlow type (expense or income).
	 * 
	 * @param cat
	 *            Category.
	 * @return A filtered list of cashflows, empty list if period or start are
	 *         null.
	 */
	public List<CashFlow> getAllWithFilter(Calendar start, Period period,
			MovementType type, Category cat) {
		// Changes won't persist after method
		start = (Calendar) start.clone();
		List<CashFlow> result = new ArrayList<CashFlow>();
		if ((start == null || period == null)) {
			return result;
		}
		try {
			Calendar end = GregorianCalendar.getInstance();
			end.setTime(start.getTime());
			if (period == Period.ONCE) {
				result.addAll(getAllFiltered(start, end, Period.ONCE, type, cat));
			}
			// Gets monthly movements
			if (period == Period.MONTHLY) {
				// Set first and last days of the month
				start.set(Calendar.DATE, 1);
				end.set(Calendar.DATE,
						end.getActualMaximum(Calendar.DAY_OF_MONTH));
				result.addAll(getAllFiltered(start, end, Period.ONCE, type, cat));
				result.addAll(getAllFiltered(start, end, Period.MONTHLY, type,
						cat));
			}
			// Gets yearly and monthly*12 movements
			if (period == Period.YEARLY) {
				// Set first and last days of the month
				start.set(Calendar.DATE, 1);
				start.set(Calendar.MONTH, Calendar.JANUARY);
				end.set(Calendar.MONTH, Calendar.DECEMBER);
				end.set(Calendar.DATE,
						end.getActualMaximum(Calendar.DAY_OF_MONTH));
				result.addAll(getAllFiltered(start, end, Period.YEARLY, type,
						cat));
				List<CashFlow> monthlyMovements = getAllFiltered(start, end,
						Period.MONTHLY, type, cat);
				for (CashFlow cashFlow : monthlyMovements) {
					Calendar periodStart = Calendar.getInstance();
					periodStart.setTime(cashFlow.getDate());
					Calendar periodEnd = Calendar.getInstance();
					if (cashFlow.getEndDate() != null) {
						periodEnd.setTime(cashFlow.getEndDate());
					} else {
						periodEnd = null;
					}
					// Checks what months movements are in period range
					for (int i = start.get(Calendar.YEAR); i <= end
							.get(Calendar.YEAR); i++) {
						for (int j = 0; j < 12; j++) {
							CashFlow c = (CashFlow) cashFlow.clone();
							Date date = c.getDate();
							date.setYear(i - 1900);
							date.setMonth(j);
							c.setDate(date);
							Calendar movementDate = Calendar.getInstance();
							movementDate.setTime(c.getDate());
							if (movementInPeriod(periodStart, periodEnd,
									movementDate)) {
								result.add(c);
							}
						}
					}
				}
				result.addAll(getAllFiltered(start, end, Period.ONCE, type, cat));
			}
		} catch (SQLException e) {
			Log.e(TAG, e.getMessage());
			return null;
		}
		return result;
	}

	/**
	 * Checks if a monthly movement is valid in the given period.
	 * 
	 * @param periodStart
	 *            Start of the period.
	 * @param periodEnd
	 *            End of the period (null if indefinite)
	 * @param movementDate
	 *            Date of the movement
	 * @return if it's valid
	 */
	private boolean movementInPeriod(Calendar periodStart, Calendar periodEnd,
			Calendar movementDate) {
		periodStart.set(Calendar.DATE, 1);
		periodStart.set(Calendar.MILLISECOND, 0);
		periodStart.set(Calendar.SECOND, 0);
		periodStart.set(Calendar.MINUTE, 0);
		periodStart.set(Calendar.HOUR_OF_DAY, 0);
		if (periodEnd != null) {
			periodEnd.set(Calendar.MILLISECOND, 999);
			periodEnd.set(Calendar.SECOND, 59);
			periodEnd.set(Calendar.MINUTE, 59);
			periodEnd.set(Calendar.HOUR_OF_DAY, 23);
			periodEnd.set(Calendar.DATE,
					periodEnd.getActualMaximum(Calendar.DATE));
		}
		if (periodStart.before(movementDate)) {
			if (periodEnd != null) {
				if (periodEnd.after(movementDate)) {
					return true;
				} else {
					return false;
				}
			} else {
				return true;
			}
		} else {
			return false;
		}
	}

	/**
	 * 
	 * @param start
	 *            Start day.
	 * @param end
	 *            End day.
	 * 
	 * @param period
	 *            Period (daily, monthly or yearly).
	 * @param type
	 *            CashFlow type (expense or income).
	 * 
	 * @param cat
	 *            Category.
	 * @return A filtered list of cashflows.
	 * @throws SQLException
	 */
	@SuppressWarnings("unchecked")
	private List<CashFlow> getAllFiltered(Calendar start, Calendar end,
			Period period, MovementType type, Category cat) throws SQLException {
		Where<CashFlowVO, Long> where = cashDao.queryBuilder().where();
		boolean needAnd = false;
		if (cat != null) {
			if (needAnd) {
				where.and();
			}
			where.eq("category_id", cat.getId());
			needAnd = true;
		}
		if (type != null) {
			if (needAnd) {
				where.and();
			}
			where.eq("movType", type.getValue());
			needAnd = true;
		}
		if (period != null) {
			if (needAnd) {
				where.and();
			}
			needAnd = true;
			where.eq("period", period.getCode());
		}

		if (start != null) {
			start.set(Calendar.MILLISECOND, 0);
			start.set(Calendar.SECOND, 0);
			start.set(Calendar.MINUTE, 0);
			start.set(Calendar.HOUR_OF_DAY, 0);
			end.set(Calendar.MILLISECOND, 999);
			end.set(Calendar.SECOND, 59);
			end.set(Calendar.MINUTE, 59);
			end.set(Calendar.HOUR_OF_DAY, 23);
			if (period == Period.ONCE) {
				if (needAnd) {
					where.and();
				}
				where.between("date", start.getTime(), end.getTime());
			} else if (needAnd) {
				where.and(
						where,
						where.and(
								where.le("date", end.getTime()),
								where.or(where.isNull("endDate"),
										where.ge("endDate", start.getTime()))));
			} else {
				where.and(
						where.le("date", end.getTime()),
						where.or(where.isNull("endDate"),
								where.ge("endDate", start.getTime())));
			}
			needAnd = true;
		}
		List<CashFlow> result = new LinkedList<CashFlow>();
		for (CashFlowVO cashFlowVO : cashDao.query(where.prepare())) {
			result.add(ModelUtilities.cashFlowValueObjToPublicObj(cashFlowVO));
		}
		where.clear();
		cashDao.queryBuilder().clear();
		return result;
	}
}
