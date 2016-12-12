package com.pujjr.pcci.service.credit;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.pujjr.common.exception.CheckFailException;
import com.pujjr.common.pager.Paged;
import com.pujjr.common.pager.PagedResult;
import com.pujjr.common.result.ResultInfo;
import com.pujjr.common.type.IdentityType;
import com.pujjr.common.type.credit.MealType;
import com.pujjr.common.type.credit.QueryProductType;
import com.pujjr.common.type.credit.QueryReasonType;
import com.pujjr.common.utils.BaseIterableUtils;
import com.pujjr.common.utils.BaseStringUtils;
import com.pujjr.common.utils.BaseUtils;
import com.pujjr.pcci.api.bean.request.CreditRequestData;
import com.pujjr.pcci.api.bean.request.QianHaiRequestData;
import com.pujjr.pcci.dal.dao.CreditQueryResultDAO;
import com.pujjr.pcci.dal.dao.CreditRequestDAO;
import com.pujjr.pcci.dal.entity.CreditCrimeInfo;
import com.pujjr.pcci.dal.entity.CreditExecution;
import com.pujjr.pcci.dal.entity.CreditPerInvest;
import com.pujjr.pcci.dal.entity.CreditQueryResult;
import com.pujjr.pcci.dal.entity.CreditRequest;
import com.pujjr.pcci.dal.entity.CreditRskdoo;
import com.pujjr.pcci.dal.entity.HundredCreditRequest;
import com.pujjr.pcci.dal.entity.QianHaiResult;
import com.pujjr.pcci.service.ParameterizedBaseService;
import com.pujjr.pcci.service.hundredcredit.HundredCreditService;
import com.pujjr.pcci.service.qhcs.QianHaiService;
import com.pujjr.pcci.service.store.StoreService;

/**
 * @author wen
 * @date 创建时间：2016年10月19日 上午10:42:27 征信查询服务
 *
 */
@Service
public class CreditService extends ParameterizedBaseService<CreditService> {

	/**
	 * 1个月前
	 */
	public final static Integer ONE_MONTH_AGO = 1;
	/**
	 * 3个月前
	 */
	public final static Integer THREE_MONTHS_AGO = 3;
	/**
	 * 6个月前
	 */
	public final static Integer SIX_MONTHS_AGO = 6;
	/**
	 * 12个月前
	 */
	public final static Integer TWELVE_MONTHS_AGO = 12;

	/**
	 * 默认时间格式
	 */
	private final static String DEFAULT_DATE_FORMAT = "yyyy-MM-dd";

	@Value("#{settings['pcci.concurrent.number']}")
	private int concurrentNumber;

	@Autowired
	private HundredCreditService hundredCreditService;

	@Autowired
	private QianHaiService qianHaiService;

	@Autowired
	private PdfService pdfService;

	@Autowired
	private CreditQueryResultDAO creditQueryResultDAO;

	@Autowired
	private CreditRequestDAO creditRequestDAO;

	@Autowired
	private StoreService storeService;

	/**
	 * 根据条件查询征信请求记录
	 * 
	 * @param paged
	 * @param beginCreditId
	 * @param endCreditId
	 * @param searchText
	 * @return
	 */
	public PagedResult<CreditRequest> searchCreditRequest(Paged paged, Long beginCreditId, Long endCreditId, String searchText, String stateTimeStr, String endTimeStr) {
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm");
		Date stateTime = null;
		Date endTime = null;
		try {
			if (StringUtils.isNotBlank(stateTimeStr)) {
				stateTime = dateFormat.parse(stateTimeStr);
			}
			if (StringUtils.isNotBlank(endTimeStr)) {
				endTime = dateFormat.parse(endTimeStr);
			}
		} catch (ParseException e) {
			logger.error("时间格式转化错误");
		}

		return creditRequestDAO.searchCreditRequest(paged, beginCreditId, endCreditId, searchText, stateTime, endTime);
	}

	/**
	 * 根据征信编号下载文件
	 * 
	 * @param creditId
	 * @return
	 */
	public ResultInfo<byte[]> downloadCreditFile(Long id) {
		ResultInfo<byte[]> resultInfo = new ResultInfo<>();
		if (id != null) {
			return pdfService.downloadPDF(id);
		} else {
			resultInfo.fail("征信流水号不能为空");
		}
		return resultInfo;
	}

	/**
	 * 征信查询请求参数验证
	 * 
	 * @param creditRequestData
	 * @return
	 */
	public ResultInfo<String> creditRequestValidate(CreditRequestData creditRequestData) {
		ResultInfo<String> resultInfo = new ResultInfo<>();
		try {
			Assert.notNull(creditRequestData, "征信请求数据不能为空");
			// TODO 唯一标识暂时自动生成
			// Assert.isTrue(StringUtils.isNotBlank(creditRequestData.getCreditId()), "征信唯一流水号不能为空");
			Assert.isTrue(StringUtils.isNotBlank(creditRequestData.getRequestUserId()), "请求用户编号/工号不能为空");
			Assert.isTrue(StringUtils.isNotBlank(creditRequestData.getIdNo()), "被查询用户证件号不能为空");
			Assert.isTrue(StringUtils.isNotBlank(creditRequestData.getMobileNo()), "被查询用户手机号不能为空");
			Assert.isTrue(StringUtils.isNotBlank(creditRequestData.getName()), "被查询用户姓名不能为空");
			Assert.notNull(creditRequestData.getReasonCode(), "查询原因不能为空");
			Assert.notNull(creditRequestData.getIdType(), "被查询用户证件类型不能为空");
			Assert.isTrue(StringUtils.isNotBlank(creditRequestData.getEntityAuthCode()), "信息主体授权码不能为空");
			Assert.isTrue(StringUtils.isNotBlank(creditRequestData.getEntityAuthDate()), "信息主体授权码授权时间不能为空");
			resultInfo.success();
		} catch (IllegalArgumentException e) {
			resultInfo.fail(e);
		}
		return resultInfo;
	}

	/**
	 * 检查请求的所有数据里,是否有重复的
	 * 
	 * @param creditRequestDataList
	 * @param creditRequestData
	 * @return
	 */
	public ResultInfo<String> isRepeatRequest(List<CreditRequestData> creditRequestDataList) {
		ResultInfo<String> resultInfo = new ResultInfo<>();
		try {
			if (creditRequestDataList == null) {
				throw new CheckFailException("获取数据失败");
			}
			for (int i = 0; i < creditRequestDataList.size(); i++) {
				CreditRequestData data1 = creditRequestDataList.get(i);
				// 与集合后面的每一个元素比较, 最后一个元素停止比较
				for (int j = i + 1; j < creditRequestDataList.size(); j++) {
					CreditRequestData data2 = creditRequestDataList.get(j);
					// 如果三要素都一致,返回失败
					if (StringUtils.equals(data1.getIdNo(), data2.getIdNo()) && StringUtils.equals(data1.getName(), data2.getName()) && StringUtils.equals(data1.getMobileNo(), data2.getMobileNo())) {
						throw new CheckFailException("表中第" + (i + 1) + "行数据与第" + (j + 1) + "行数据重复");
					}
				}
			}
			resultInfo.success();
		} catch (CheckFailException e) {
			resultInfo.fail(e);
		}
		return resultInfo;
	}

	/**
	 * 获得一个征信请求数据填装bean
	 * 
	 * @param creditId
	 * @param requestUserId
	 * @param idNo
	 * @param mobileNo
	 * @param name
	 * @param reasonType
	 * @param identityType
	 * @param entityAuthCode
	 * @param entityAuthDate
	 * @return
	 */
	public CreditRequestData getCreditRequestData(String creditId, String requestUserId, String idNo, String mobileNo, String name, QueryReasonType reasonType, IdentityType identityType, String entityAuthCode, String entityAuthDate) {
		CreditRequestData creditRequestData = new CreditRequestData();
		creditRequestData.setCreditId(StringUtils.leftPad(creditId, 10, "0"));
		creditRequestData.setRequestUserId(requestUserId);
		creditRequestData.setIdNo(idNo);
		creditRequestData.setMobileNo(mobileNo);
		creditRequestData.setName(name);
		creditRequestData.setReasonCode(reasonType.getCode());
		creditRequestData.setIdType(identityType.getCode());
		creditRequestData.setEntityAuthCode(entityAuthCode);
		creditRequestData.setEntityAuthDate(entityAuthDate);
		return creditRequestData;
	}

	/**
	 * 根据征信查询结果获得征信查询请求及简要信息
	 * 
	 * @param creditQueryResultList
	 * @return
	 */
	public List<CreditRequest> getCreditRequestList(List<CreditQueryResult> creditQueryResultList) {
		List<CreditRequest> creditRequestList = new ArrayList<>();
		for (CreditQueryResult creditQueryResult : creditQueryResultList) {
			if (creditQueryResult != null) {
				creditRequestList.add(creditQueryResult.getCreditRequest());
			}
		}
		return creditRequestList;
	}

	/**
	 * 根据征信查询结果生成PDF文件
	 * 
	 * @param creditRequestData
	 * @return
	 */
	public ResultInfo<CreditQueryResult> creditQueryAndStore(CreditRequestData creditRequestData) {
		ResultInfo<CreditQueryResult> creditQueryResult = creditQuery(creditRequestData);
		if (creditQueryResult.isSuccess()) {
			return pdfService.creditQueryResultToPDF(creditQueryResult.getData());
		}
		return creditQueryResult;
	}

	/**
	 * 并发执行征信查询
	 * 
	 * @param creditRequestDataList
	 * @return
	 */
	public ResultInfo<String> concurrenceCreditQuery(List<CreditRequestData> creditRequestDataList) {
		ResultInfo<String> resultInfo = new ResultInfo<>();
		// 线程池，超过数量则堵塞线程
		ExecutorService executor = Executors.newFixedThreadPool(concurrentNumber);
		List<CompletableFuture<ResultInfo<CreditQueryResult>>> futureList = new ArrayList<>();
		List<CreditQueryResult> creditQueryResultList = new ArrayList<>();
		try {
			// 并发执行前进行登录
			String tokenid = hundredCreditService.login();
			// 循环生并发
			for (CreditRequestData CreditRequestData : creditRequestDataList) {
				CompletableFuture<ResultInfo<CreditQueryResult>> future = CompletableFuture.supplyAsync(new Supplier<ResultInfo<CreditQueryResult>>() {
					@Override
					public ResultInfo<CreditQueryResult> get() {
						ResultInfo<CreditQueryResult> result = creditQuery(CreditRequestData, tokenid);
						if (result.isSuccess() && result.getData() != null) {
							CreditQueryResult creditQueryResult = result.getData();
							creditQueryResultList.add(creditQueryResult);
						}
						return result;
					}
				}, executor);
				futureList.add(future);
			}
			CompletableFuture<Void> allDoneFuture = CompletableFuture.allOf(futureList.toArray(new CompletableFuture[futureList.size()]));

			allDoneFuture.join();
			Long maxCreditId = 0l;
			Long minCreditId = 0l;
			for (CreditQueryResult creditQueryResult : creditQueryResultList) {
				CreditRequest creditRequest = creditQueryResult.getCreditRequest();
				maxCreditId = Long.max(maxCreditId, creditRequest.getId());
				minCreditId = minCreditId == 0l ? creditRequest.getId() : Long.min(minCreditId, creditRequest.getId());
			}
			resultInfo.success("searchCredit?beginCreditId=" + minCreditId + "&endCreditId=" + maxCreditId);
		} catch (Exception e) {
			e.printStackTrace();
			resultInfo.fail(e);
		} finally {
			executor.shutdown();
		}
		return resultInfo;
	}

	/**
	 * 调用征信查询
	 * 
	 * @param creditRequestData
	 * @return
	 */
	public ResultInfo<CreditQueryResult> creditQuery(CreditRequestData creditRequestData) {
		return creditQuery(creditRequestData, null);
	}

	/**
	 * 调用征信查询
	 * 
	 * @param creditRequestData
	 * @return
	 */
	public ResultInfo<CreditQueryResult> creditQuery(CreditRequestData creditRequestData, String tokenid) {
		ResultInfo<CreditQueryResult> resultInfo = new ResultInfo<>();
		// 开始调用
		CreditQueryResult creditQueryResult = new CreditQueryResult();
		CreditRequest creditRequest = new CreditRequest();
		// TODO 验证结果
		ResultInfo<String> validateResult = creditRequestValidate(creditRequestData);
		if (validateResult.isSuccess()) {
			BeanUtils.copyProperties(creditRequestData, creditRequest);
			creditQueryResult.setCreditRequest(creditRequest);
			sandCreditQuery(creditQueryResult, tokenid);
		}
		creditQueryResultDAO.save(creditQueryResult);
		resultInfo.success(creditQueryResult);
		return resultInfo;
	}

	public ResultInfo<CreditQueryResult> creditQueryAgain(String creditId) {
		CreditQueryResult creditQueryResult = creditQueryResultDAO.findCreditQueryResultByCreditId(creditId);

		ResultInfo<CreditQueryResult> resultInfo = new ResultInfo<>();
		// 开始调用
		CreditRequest creditRequest = creditQueryResult.getCreditRequest();
		if (StringUtils.isNotBlank(creditRequest.getOssKey())) {
			storeService.delete(creditRequest.getOssKey());
			creditRequest.setOssKey(null);
		}
		sandCreditQuery(creditQueryResult, null);
		creditQueryResultDAO.update(creditQueryResult);
		resultInfo.success(creditQueryResult);
		return resultInfo;
	}

	/**
	 * 执行征信查询
	 * 
	 * @param creditQueryResult
	 * @param tokenid
	 * @return
	 */
	public ResultInfo<CreditQueryResult> sandCreditQuery(CreditQueryResult creditQueryResult, String tokenid) {
		ResultInfo<CreditQueryResult> resultInfo = new ResultInfo<>();
		// 开始调用
		CreditRequest creditRequest = creditQueryResult.getCreditRequest();
		List<String> errMsg = new ArrayList<>();
		long beginTime = System.currentTimeMillis();
		try {
			String creditId = getNewCreditId(creditRequest.getIdNo());
			creditQueryResult.setCreditId(creditId);

			creditRequest.setCreditId(creditId);
			creditRequest.setRequestDate(new Date());// 征信查询发起时间
			creditRequest.setName(StringUtils.remove(creditRequest.getName(), "·"));
			creditRequest.setEntityAuthDate(StringUtils.replace(creditRequest.getEntityAuthDate(), "/", "-"));

			System.out.println("百融登录前:" + (System.currentTimeMillis() - beginTime));
			beginTime = System.currentTimeMillis();
			// 百融登录获取
			if (StringUtils.isBlank(tokenid)) {
				tokenid = hundredCreditService.login();
			}
			System.out.println("百融登录耗时:" + (System.currentTimeMillis() - beginTime));
			beginTime = System.currentTimeMillis();
			// 百融接口调用请求数据
			HundredCreditRequest hcRequestData = new HundredCreditRequest();
			hcRequestData.setTokenid(tokenid);// no
			hcRequestData.setRequestUserId(creditRequest.getRequestUserId());
			hcRequestData.setId(creditRequest.getIdNo());
			hcRequestData.setCell(creditRequest.getMobileNo());
			hcRequestData.setName(creditRequest.getName());

			// 前海接口调用请求数据
			QianHaiRequestData qhRequestData = new QianHaiRequestData();
			qhRequestData.setSubmitTransNo(creditId);
			qhRequestData.setIdNo(creditRequest.getIdNo());
			qhRequestData.setDriverNo(creditRequest.getIdNo());
			qhRequestData.setReasonCode(creditRequest.getReasonCode());
			qhRequestData.setReasonNo(creditRequest.getReasonCode());
			qhRequestData.setIdType(creditRequest.getIdType());
			qhRequestData.setName(creditRequest.getName());
			qhRequestData.setMobileNo(creditRequest.getMobileNo());
			qhRequestData.setEntityAuthCode(creditRequest.getEntityAuthCode());
			qhRequestData.setEntityAuthDate(creditRequest.getEntityAuthDate());

			System.out.println("接口配置耗时:" + (System.currentTimeMillis() - beginTime));
			beginTime = System.currentTimeMillis();

			/*************** 百融相关 ****************/
			// 个人不良信息
			try {
				crimeinfoQuery(creditQueryResult, hcRequestData);
			} catch (Exception e) {
				errMsg.add("百融个人不良信息-" + e.getMessage());
			}
			System.out.println("不良信息耗时:" + (System.currentTimeMillis() - beginTime));
			beginTime = System.currentTimeMillis();

			// 被执行人
			try {
				executionQuery(creditQueryResult, hcRequestData);
			} catch (Exception e) {
				errMsg.add("百融被执行记录-" + e.getMessage());
			}
			System.out.println("被执行耗时:" + (System.currentTimeMillis() - beginTime));
			beginTime = System.currentTimeMillis();

			// 百融多次申请
			try {
				applyloanQuery(creditQueryResult, hcRequestData);
			} catch (Exception e) {
				errMsg.add("百融多次贷款申请-" + e.getMessage());
			}
			System.out.println("百融多次贷款申请耗时:" + (System.currentTimeMillis() - beginTime));
			beginTime = System.currentTimeMillis();

			// TODO 对外投资 太贵了 查不起 暂时注释掉
			try {
				// perinvestQuery(creditQueryResult, hcRequestData);
			} catch (Exception e) {
				errMsg.add("百融对外投资-" + e.getMessage());
			}

			/*************** 前海相关 ****************/
			// 常贷客值装入
			try {
				loaneeQuery(creditQueryResult, qhRequestData, creditId);
			} catch (Exception e) {
				errMsg.add("前海常贷客-" + e.getMessage());
			}
			System.out.println("常贷客耗时:" + (System.currentTimeMillis() - beginTime));
			beginTime = System.currentTimeMillis();

			// 反欺诈
			try {
				antifrauddooQuery(creditQueryResult, qhRequestData, creditId);
			} catch (Exception e) {
				errMsg.add("前海反欺诈-" + e.getMessage());
			}
			System.out.println("反欺诈耗时:" + (System.currentTimeMillis() - beginTime));
			beginTime = System.currentTimeMillis();

			// 风险度
			try {
				rskdooQuery(creditQueryResult, qhRequestData, creditId);
			} catch (Exception e) {
				errMsg.add("前海风险度-" + e.getMessage());
			}
			System.out.println("风险度耗时:" + (System.currentTimeMillis() - beginTime));
			beginTime = System.currentTimeMillis();

			// 一鉴通
			try {
				eChkPkgs(creditQueryResult, qhRequestData, creditId);
			} catch (Exception e) {
				errMsg.add("前海一鉴通-" + e.getMessage());
			}
			System.out.println("一鉴通耗时:" + (System.currentTimeMillis() - beginTime));
			beginTime = System.currentTimeMillis();

			// 驾驶证
			try {
				drvcert2cmpincQuery(creditQueryResult, qhRequestData, creditId);
			} catch (Exception e) {
				errMsg.add("前海驾驶证-" + e.getMessage());
			}
			System.out.println("驾驶证耗时:" + (System.currentTimeMillis() - beginTime));
			beginTime = System.currentTimeMillis();

			/*************** 接口调用完成 ****************/
			// 根据结果计算风险
			calculatedRisk(creditQueryResult);
			System.out.println("风险计算耗时:" + (System.currentTimeMillis() - beginTime));
			beginTime = System.currentTimeMillis();

			// 保存数据
		} catch (Exception e) {
			logger.error("查询征信报告出现错误", e);
			errMsg.add("查询征信报告出现错误:" + e.getMessage());
		}
		creditRequest.setErrMsg(StringUtils.join(errMsg, ","));
		System.out.println(creditRequest.getName() + "---" + creditRequest.getCreditId() + "--------总耗时：" + (System.currentTimeMillis() - creditRequest.getRequestDate().getTime()));
		resultInfo.success(creditQueryResult);
		return resultInfo;
	}

	/**
	 * 获得一个新的唯一征信流水号
	 * 
	 * @param IdNo
	 * @return
	 */
	private String getNewCreditId(String IdNo) {
		String prefix = StringUtils.substring(StringUtils.leftPad(IdNo, 8, "0"), -8);
		return (prefix + BaseUtils.get16UUID());
	}

	/**
	 * 风险度计算
	 * 
	 * @param creditQueryResult
	 */
	public void calculatedRisk(CreditQueryResult creditQueryResult) {
		CreditRequest creditRequest = creditQueryResult.getCreditRequest();
		Integer riskLevel = CreditRequest.RISK_LEVEL_NONE;

		/************** 法院被执行 *****************/
		List<CreditExecution> creditExecutionList = creditQueryResult.getCreditExecutionList();
		// 无记录
		if (BaseIterableUtils.isNotEmpty(creditExecutionList)) {
			// 有记录
			for (CreditExecution creditExecution : creditExecutionList) {
				// 有失信被执行记录 = 高风险
				if (CreditExecution.EXECUTION_TYPE_BAD.equals(creditExecution.getExecutionType())) {
					riskLevel = Integer.max(riskLevel, CreditRequest.RISK_LEVEL_HIGH);
					// 有被执行记录 = 低风险
				} else if (CreditExecution.EXECUTION_TYPE_EXECUT.equals(creditExecution.getExecutionType())) {
					if (StringUtils.isNotBlank(creditExecution.getEx_execut_statute())) {
						riskLevel = Integer.max(riskLevel, CreditRequest.RISK_LEVEL_LOW);
						// 有被执行记录,且被执行记录有在"执行中"的 = 高风险
						if (creditExecution.getEx_execut_statute().contains("执行中")) {
							riskLevel = Integer.max(riskLevel, CreditRequest.RISK_LEVEL_HIGH);
						}
					}
				}
			}
		}

		/************** 信贷记录 *****************/
		// 获得前海,百融 1 3 6 12个月贷款申请数统计
		// 前海
		int qh_id_m1 = creditQueryResult.getQh_m1_id_loan_num();
		int qh_id_m3 = creditQueryResult.getQh_m3_id_loan_num();
		int qh_id_m6 = creditQueryResult.getQh_m6_id_loan_num();
		int qh_id_m12 = creditQueryResult.getQh_m12_id_loan_num();
		// 百融 身份证
		int br_id_m1 = creditQueryResult.getBr_m1_id_loan_num();
		int br_id_m3 = creditQueryResult.getBr_m3_id_loan_num();
		int br_id_m6 = creditQueryResult.getBr_m6_id_loan_num();
		int br_id_m12 = creditQueryResult.getBr_m12_id_loan_num();
		// 百融 手机
		int br_cell_m1 = creditQueryResult.getBr_m1_cell_loan_num();
		int br_cell_m3 = creditQueryResult.getBr_m3_cell_loan_num();
		int br_cell_m6 = creditQueryResult.getBr_m6_cell_loan_num();
		int br_cell_m12 = creditQueryResult.getBr_m12_cell_loan_num();

		// 检查一月内
		List<Integer> loanList_m1 = new ArrayList<>();
		loanList_m1.add(qh_id_m1);
		loanList_m1.add(br_id_m1);
		loanList_m1.add(br_cell_m1);
		for (Integer loan_m1 : loanList_m1) {
			// 超过4次的为高风险
			if (loan_m1 > 4) {
				riskLevel = Integer.max(riskLevel, CreditRequest.RISK_LEVEL_HIGH);
			}
		}
		// 检查3月内
		List<Integer> loanList_m3 = new ArrayList<>();
		loanList_m3.add(qh_id_m3);
		loanList_m3.add(br_id_m3);
		loanList_m3.add(br_cell_m3);
		for (Integer loan_m3 : loanList_m3) {
			// 大于6次的为高风险
			if (loan_m3 > 6) {
				riskLevel = Integer.max(riskLevel, CreditRequest.RISK_LEVEL_HIGH);
			}
		}
		// 任何月份有数据为低风险
		List<Integer> loanList_all = new ArrayList<>();
		loanList_all.add(br_id_m6);
		loanList_all.add(br_id_m12);
		loanList_all.add(br_cell_m6);
		loanList_all.add(br_cell_m12);
		loanList_all.add(qh_id_m6);
		loanList_all.add(qh_id_m12);
		// 拼接各个月份集合
		loanList_all = BaseIterableUtils.union(loanList_m1, loanList_m3, loanList_all);
		for (Integer loan : loanList_all) {
			// 任意一月有数据为低风险
			if (loan > 0) {
				riskLevel = Integer.max(riskLevel, CreditRequest.RISK_LEVEL_LOW);
			}
		}

		/************** 前海反欺诈 *****************/
		// 命中第三方标注黑名单 或 命中第三方标注黑名单 中命中一个 则为高风险
		if (StringUtils.equals(creditQueryResult.getIsMachdBlMakt(), "1") || StringUtils.equals(creditQueryResult.getIsMachFraud(), "1")) {
			riskLevel = Integer.max(riskLevel, CreditRequest.RISK_LEVEL_HIGH);
		}

		/************** 前海风险度 *****************/
		// 风险的分不为空或99权限不足 低风险
		List<CreditRskdoo> creditRskdooList = creditQueryResult.getCreditRskdooList();
		int rskdooCount = 0;
		for (CreditRskdoo creditRskdoo : creditRskdooList) {
			if (StringUtils.isNotBlank(creditRskdoo.getDataBuildTime()) && StringUtils.equals("99", creditRskdoo.getDataBuildTime())) {
				riskLevel = Integer.max(riskLevel, CreditRequest.RISK_LEVEL_LOW);
				Date requestDate = creditRequest.getRequestDate();
				try {
					Date buildTime = DateUtils.parseDate(creditRskdoo.getDataBuildTime(), DEFAULT_DATE_FORMAT);
					// 九十天内风险得分20以上的为 高风险
					if (requestDate.before(DateUtils.addDays(buildTime, 90)) && Integer.parseInt(creditRskdoo.getRskScore()) > 20) {
						riskLevel = Integer.max(riskLevel, CreditRequest.RISK_LEVEL_HIGH);
					}
					// 365天内风险得分30分以上的为高风险
					if (requestDate.before(DateUtils.addDays(buildTime, 365))) {
						if (Integer.parseInt(creditRskdoo.getRskScore()) > 30) {
							riskLevel = Integer.max(riskLevel, CreditRequest.RISK_LEVEL_HIGH);
						}
						// 365天内20以上记一次
						if (Integer.parseInt(creditRskdoo.getRskScore()) > 20) {
							rskdooCount = rskdooCount + 1;
						}
					}
					// 365天内<2次20分>
					if (rskdooCount >= 2) {
						riskLevel = Integer.max(riskLevel, CreditRequest.RISK_LEVEL_HIGH);
					}
				} catch (ParseException e) {
					logger.debug("时间格式转化错误", e);
				}
			}
		}

		/************** 前海手机 *****************/
		// TODO
		if (StringUtils.equals(creditQueryResult.getOwnerMobileStatus(), "1")) {
			// 使用时间分数为 1：(0-1],2:(1-2],3:(2-6],30:(0,6]的 则为低风险
			if (BaseStringUtils.equalsAny(creditQueryResult.getUseTimeScore(), "1", "2", "3", "30")) {
				riskLevel = Integer.max(riskLevel, CreditRequest.RISK_LEVEL_LOW);
			}
			// 出现任意“不一致”的情况
			if (BaseStringUtils.equalsAny(creditQueryResult.getIsOwnerMobile(), "1", "3")) {
				riskLevel = Integer.max(riskLevel, CreditRequest.RISK_LEVEL_LOW);
			}
		} else if (StringUtils.isNotBlank(creditQueryResult.getOwnerMobileStatus())) {
			// 手机状态不为1则低风险
			riskLevel = Integer.max(riskLevel, CreditRequest.RISK_LEVEL_LOW);
		}

		/************** 前海驾驶证 *****************/
		if (StringUtils.isNotBlank(creditQueryResult.getChkDriverNo())) {
			// 驾驶证验证为0 验证未通过 高风险
			if (StringUtils.equals(creditQueryResult.getChkDriverNo(), "0")) {
				riskLevel = Integer.max(riskLevel, CreditRequest.RISK_LEVEL_HIGH);
			}
			// 驾驶证状态不正常(A为正常),低风险
			if (!StringUtils.equals(creditQueryResult.getChkStatus(), "A")) {
				riskLevel = Integer.max(riskLevel, CreditRequest.RISK_LEVEL_LOW);
			}
		}

		/************** 保存基本风险分析结果 **************/
		creditRequest.setRiskLevel(riskLevel);

		/************** 个人不良信息风险 *****************/
		// 如果是个人不良信息为空 则记录为无数据 ,如果记录不为空则记录为需其他渠道查询
		if (BaseIterableUtils.isNotEmpty(creditQueryResult.getCreditCrimeInfoList())) {
			creditRequest.setCriminalRecord(CreditRequest.CRIMINAL_YES);
		} else {
			creditRequest.setCriminalRecord(CreditRequest.CRIMINAL_NO);
		}

		/************** 对外投资风险 *****************/
		// 如果是对外投资为空 则记录为无数据 ,如果记录不为空则记录为有数据
		if (BaseIterableUtils.isNotEmpty(creditQueryResult.getCreditPerInvestList())) {
			creditRequest.setInvestInfo(CreditRequest.INVEST_YES);
		} else {
			creditRequest.setInvestInfo(CreditRequest.INVEST_NO);
		}

		/************** 基本风险分析完成 **************/
		creditQueryResult.setCreditRequest(creditRequest);
	}

	/**
	 * 个人不良信息查询
	 * 
	 * @param creditQueryResult
	 * @param hcRequestData
	 */
	private void crimeinfoQuery(CreditQueryResult creditQueryResult, HundredCreditRequest hcRequestData) throws Exception {
		ResultInfo<String> crimeInfoResult = hundredCreditService.hundredCreditDasRequest(hcRequestData, MealType.CrimeInfo);
		if (crimeInfoResult.isSuccess()) {
			JSONObject crimeInfoJSON = JSON.parseObject(crimeInfoResult.getData());
			JSONArray priskChecks = crimeInfoJSON.getJSONObject("product").getJSONArray("priskChecks");
			List<CreditCrimeInfo> creditCrimeInfoList = new ArrayList<>();
			for (int i = 0; priskChecks != null && i < priskChecks.size(); i++) {
				JSONObject priskCheck = priskChecks.getJSONObject(i);
				JSONArray caseDetails = new JSONArray();
				if (priskCheck.get("caseDetail") != null) {
					if (priskCheck.get("caseDetail") instanceof JSONObject) {
						caseDetails.add(priskCheck.getJSONObject("caseDetail"));
					} else if (priskCheck.get("caseDetail") instanceof JSONArray) {
						caseDetails = priskCheck.getJSONArray("caseDetail");
					} else {
						throw new CheckFailException("数据格式获取错误");
					}
				}
				for (int j = 0; caseDetails != null && j < caseDetails.size(); j++) {
					CreditCrimeInfo creditCrimeInfo = new CreditCrimeInfo();
					JSONObject caseDetailJSON = caseDetails.getJSONObject(j);
					if (caseDetailJSON != null) {
						if (caseDetailJSON.getJSONObject("caseSource") != null) {
							creditCrimeInfo.setCaseSource(caseDetailJSON.getJSONObject("caseSource").getString("#text"));
						}
						if (caseDetailJSON.getJSONObject("caseTime") != null) {
							creditCrimeInfo.setCaseTime(caseDetailJSON.getJSONObject("caseTime").getString("#text"));
						}
						if (caseDetailJSON.getJSONObject("caseType") != null) {
							creditCrimeInfo.setCaseType(caseDetailJSON.getJSONObject("caseType").getString("#text"));
						}
						creditCrimeInfoList.add(creditCrimeInfo);
					}
				}
			}
			creditQueryResult.setCreditCrimeInfoList(creditCrimeInfoList);
		} else {
			throw new CheckFailException(crimeInfoResult.getMsg());
		}
	}

	/**
	 * 法院被执行人查询
	 * 
	 * @param creditQueryResult
	 * @param hcRequestData
	 */
	private void executionQuery(CreditQueryResult creditQueryResult, HundredCreditRequest hcRequestData) throws Exception {
		ResultInfo<String> executionResult = hundredCreditService.hundredCreditTerRequest(hcRequestData, MealType.Execution);
		if (executionResult.isSuccess()) {
			JSONObject executionJSON = JSON.parseObject(executionResult.getData());
			List<CreditExecution> creditExecutionList = new ArrayList<>();
			String ex_bad = "ex_bad";
			String ex_execut = "ex_execut";
			for (int i = 1; i <= 10; i++) {
				CreditExecution creditExecution = new CreditExecution();
				if (StringUtils.contains(executionResult.getData(), ex_bad + i)) {
					// 失信被执行记录
					creditExecution.setEx_bad_datatype(executionJSON.getString(ex_bad + i + "_datatype"));
					creditExecution.setEx_bad_court(executionJSON.getString(ex_bad + i + "_court"));
					creditExecution.setEx_bad_time(executionJSON.getString(ex_bad + i + "_time"));
					creditExecution.setEx_bad_casenum(executionJSON.getString(ex_bad + i + "_casenum"));
					creditExecution.setEx_bad_money(executionJSON.getString(ex_bad + i + "_money"));
					creditExecution.setEx_bad_base(executionJSON.getString(ex_bad + i + "_base"));
					creditExecution.setEx_bad_basecompany(executionJSON.getString(ex_bad + i + "_basecompany"));
					creditExecution.setEx_bad_performance(executionJSON.getString(ex_bad + i + "_performance"));
					creditExecution.setEx_bad_concretesituation(executionJSON.getString(ex_bad + i + "_concretesituation"));
					creditExecution.setEx_bad_breaktime(executionJSON.getString(ex_bad + i + "_breaktime"));
					creditExecution.setExecutionType(CreditExecution.EXECUTION_TYPE_BAD);
					creditExecutionList.add(creditExecution);
				}
				if (StringUtils.contains(executionResult.getData(), ex_execut + i)) {
					creditExecution.setEx_execut_datatype(executionJSON.getString(ex_execut + i + "_datatype"));
					creditExecution.setEx_execut_court(executionJSON.getString(ex_execut + i + "_court"));
					creditExecution.setEx_execut_time(executionJSON.getString(ex_execut + i + "_time"));
					creditExecution.setEx_execut_casenum(executionJSON.getString(ex_execut + i + "_casenum"));
					creditExecution.setEx_execut_money(executionJSON.getString(ex_execut + i + "_money"));
					creditExecution.setEx_execut_statute(executionJSON.getString(ex_execut + i + "_statute"));
					creditExecution.setEx_execut_basic(executionJSON.getString(ex_execut + i + "_basic"));
					creditExecution.setEx_execut_basiccourt(executionJSON.getString(ex_execut + i + "_basiccourt"));
					creditExecution.setExecutionType(CreditExecution.EXECUTION_TYPE_EXECUT);
					creditExecutionList.add(creditExecution);
				}
			}
			creditQueryResult.setCreditExecutionList(creditExecutionList);
		} else {
			throw new CheckFailException(executionResult.getMsg());
		}
	}

	/**
	 * 常贷客
	 * 
	 * @param creditQueryResult
	 * @param qhRequestData
	 * @param transNo
	 */
	private void loaneeQuery(CreditQueryResult creditQueryResult, QianHaiRequestData qhRequestData, String transNo) throws Exception {
		ResultInfo<QianHaiResult> qianHaiResult_8037 = qianHaiService.sandQianHaiRequest(transNo, qhRequestData, QueryProductType.MSC8037);
		if (qianHaiResult_8037.isSuccess()) {
			QianHaiResult qianHaiResults = qianHaiResult_8037.getData();
			if (StringUtils.isNotBlank(qianHaiResults.getBusiDate())) {
				List<String> busiDateArray = JSON.parseArray(qianHaiResults.getBusiDate(), String.class);
				int qh_m1_id_loan_num = 0;
				int qh_m3_id_loan_num = 0;
				int qh_m6_id_loan_num = 0;
				int qh_m12_id_loan_num = 0;
				for (String busiDateString : busiDateArray) {
					if (StringUtils.isNotBlank(busiDateString)) {
						Date busiDate = DateUtils.parseDate(busiDateString, "yyyy-MM-dd");
						Date nowDate = new Date();
						// 是否十二月以内
						if (busiDate != null && nowDate.before(DateUtils.addMonths(busiDate, TWELVE_MONTHS_AGO))) {
							qh_m12_id_loan_num++;
							// 是否六月以内
							if (nowDate.before(DateUtils.addMonths(busiDate, SIX_MONTHS_AGO))) {
								qh_m6_id_loan_num++;
								// 是否三月以内
								if (nowDate.before(DateUtils.addMonths(busiDate, THREE_MONTHS_AGO))) {
									qh_m3_id_loan_num++;
									// 是否一月以内
									if (nowDate.before(DateUtils.addMonths(busiDate, ONE_MONTH_AGO))) {
										qh_m1_id_loan_num++;
									}
								}
							}
						}
						// qh_m1_id_loan_num = busiDate.before(DateUtils.addMonths(nowDate, ONE_MONTH_AGO)) ? qh_m1_id_loan_num + 1 : qh_m1_id_loan_num;
						// qh_m3_id_loan_num = busiDate.before(DateUtils.addMonths(nowDate, THREE_MONTHS_AGO)) ? qh_m3_id_loan_num + 1 : qh_m3_id_loan_num;
						// qh_m6_id_loan_num = busiDate.before(DateUtils.addMonths(nowDate, SIX_MONTHS_AGO)) ? qh_m6_id_loan_num + 1 : qh_m6_id_loan_num;
						// qh_m12_id_loan_num = busiDate.before(DateUtils.addMonths(nowDate, TWELVE_MONTHS_AGO)) ? qh_m12_id_loan_num + 1 : qh_m12_id_loan_num;
					}
				}
				creditQueryResult.setQh_m1_id_loan_num(qh_m1_id_loan_num);
				creditQueryResult.setQh_m3_id_loan_num(qh_m3_id_loan_num);
				creditQueryResult.setQh_m6_id_loan_num(qh_m6_id_loan_num);
				creditQueryResult.setQh_m12_id_loan_num(qh_m12_id_loan_num);
			}
		} else {
			throw new Exception(qianHaiResult_8037.getMsg());
		}
	}

	/**
	 * 百融多次贷款记录
	 * 
	 * @param creditQueryResult
	 * @param hcRequestData
	 */
	private void applyloanQuery(CreditQueryResult creditQueryResult, HundredCreditRequest hcRequestData) throws Exception {
		ResultInfo<String> applyloanResult = hundredCreditService.hundredCreditTerRequest(hcRequestData, MealType.ApplyLoanStr, MealType.ApplyLoan);
		if (applyloanResult.isSuccess()) {
			JSONObject applyloanJSON = JSON.parseObject(applyloanResult.getData());
			// 身份证
			creditQueryResult.setBr_m1_id_loan_num(applyloanJSON.getIntValue("als_m1_id_bank_allnum") + applyloanJSON.getIntValue("als_m1_id_nbank_allnum"));
			creditQueryResult.setBr_m3_id_loan_num(applyloanJSON.getIntValue("al_m3_id_bank_allnum") + applyloanJSON.getIntValue("al_m3_id_notbank_allnum"));
			creditQueryResult.setBr_m6_id_loan_num(applyloanJSON.getIntValue("al_m6_id_bank_allnum") + applyloanJSON.getIntValue("al_m6_id_notbank_allnum"));
			creditQueryResult.setBr_m12_id_loan_num(applyloanJSON.getIntValue("al_m12_id_bank_allnum") + applyloanJSON.getIntValue("al_m12_id_bank_selfnum") + applyloanJSON.getIntValue("al_m12_id_notbank_allnum") + applyloanJSON.getIntValue("al_m12_id_notbank_selfnum"));
			// 手机
			creditQueryResult.setBr_m1_cell_loan_num(applyloanJSON.getIntValue("als_m1_cell_bank_allnum") + applyloanJSON.getIntValue("als_m1_cell_nbank_allnum"));
			creditQueryResult.setBr_m3_cell_loan_num(applyloanJSON.getIntValue("al_m3_cell_bank_allnum") + applyloanJSON.getIntValue("al_m3_cell_notbank_allnum"));
			creditQueryResult.setBr_m6_cell_loan_num(applyloanJSON.getIntValue("al_m6_cell_bank_allnum") + applyloanJSON.getIntValue("al_m6_cell_notbank_allnum"));
			creditQueryResult.setBr_m12_cell_loan_num(applyloanJSON.getIntValue("al_m12_cell_bank_allnum") + applyloanJSON.getIntValue("al_m12_cell_bank_selfnum") + applyloanJSON.getIntValue("al_m12_cell_notbank_allnum") + applyloanJSON.getIntValue("al_m12_cell_notbank_selfnum"));
		} else {
			throw new CheckFailException(applyloanResult.getMsg());
		}
	}

	/**
	 * 反欺诈
	 * 
	 * @param creditQueryResult
	 * @param qhRequestData
	 * @param transNo
	 */
	private void antifrauddooQuery(CreditQueryResult creditQueryResult, QianHaiRequestData qhRequestData, String transNo) throws Exception {
		ResultInfo<QianHaiResult> qianHaiResult_8075 = qianHaiService.sandQianHaiRequest(transNo, qhRequestData, QueryProductType.MSC8075);
		if (qianHaiResult_8075.isSuccess()) {
			QianHaiResult qianHaiResults = qianHaiResult_8075.getData();
			creditQueryResult.setIsMachdBlMakt(qianHaiResults.getIsMachdBlMakt());
			creditQueryResult.setIsMachFraud(qianHaiResults.getIsMachFraud());
		} else {
			throw new Exception(qianHaiResult_8075.getMsg());
		}
	}

	/**
	 * 风险度
	 * 
	 * @param creditQueryResult
	 * @param qhRequestData
	 * @param transNo
	 */
	private void rskdooQuery(CreditQueryResult creditQueryResult, QianHaiRequestData qhRequestData, String transNo) throws Exception {
		List<QianHaiRequestData> requestList = new ArrayList<>();
		requestList.add(qhRequestData);
		ResultInfo<List<QianHaiResult>> qianHaiResult_8036 = qianHaiService.sandQianHaiRequest(transNo, requestList, QueryProductType.MSC8036);
		if (qianHaiResult_8036.isSuccess()) {
			List<QianHaiResult> qianHaiResults = qianHaiResult_8036.getData();
			List<CreditRskdoo> rskdooList = new ArrayList<>();
			for (QianHaiResult qianHaiResult : qianHaiResults) {
				// 如果成功E000000
				if (StringUtils.equals("E000000", qianHaiResult.getErCode())) {
					CreditRskdoo creditRskdoo = new CreditRskdoo();
					creditRskdoo.setSourceId(qianHaiResult.getSourceId());
					creditRskdoo.setRskScore(qianHaiResult.getRskScore());
					creditRskdoo.setRskMark(qianHaiResult.getRskMark());
					creditRskdoo.setDataBuildTime(qianHaiResult.getDataBuildTime());
					rskdooList.add(creditRskdoo);
				}
			}
			creditQueryResult.setCreditRskdooList(rskdooList);
		} else {
			throw new Exception(qianHaiResult_8036.getMsg());
		}
	}

	/**
	 * 一鉴通
	 * 
	 * @param creditQueryResult
	 * @param qhRequestData
	 * @param transNo
	 */
	private void eChkPkgs(CreditQueryResult creditQueryResult, QianHaiRequestData qhRequestData, String transNo) throws Exception {
		ResultInfo<QianHaiResult> qianHaiResult_8107 = qianHaiService.sandQianHaiRequest(transNo, qhRequestData, QueryProductType.MSC8107);
		if (qianHaiResult_8107.isSuccess()) {
			QianHaiResult qianHaiResults = qianHaiResult_8107.getData();
			creditQueryResult.setIsOwnerMobile(qianHaiResults.getIsOwnerMobileII());
			creditQueryResult.setOwnerMobileStatus(qianHaiResults.getOwnerMobileStatusII());
			creditQueryResult.setUseTimeScore(qianHaiResults.getUseTimeScoreII());
		} else {
			throw new Exception(qianHaiResult_8107.getMsg());
		}
	}

	/**
	 * 驾驶证状态
	 * 
	 * @param creditQueryResult
	 * @param qhRequestData
	 * @param transNo
	 */
	private void drvcert2cmpincQuery(CreditQueryResult creditQueryResult, QianHaiRequestData qhRequestData, String transNo) throws Exception {
		ResultInfo<QianHaiResult> qianHaiResult_8081 = qianHaiService.sandQianHaiRequest(transNo, qhRequestData, QueryProductType.MSC8081);
		if (qianHaiResult_8081.isSuccess()) {
			QianHaiResult result_8078 = qianHaiResult_8081.getData();
			qhRequestData.setQueryId(result_8078.getQueryId());
			int max = 20;
			for (int i = 0; i <= max; i++) {
				Thread.sleep(3000);
				ResultInfo<QianHaiResult> qianHaiResult_8079 = qianHaiService.sandQianHaiRequest(getNewCreditId(qhRequestData.getIdNo()), qhRequestData, QueryProductType.MSC8079);
				if (qianHaiResult_8079.isSuccess()) {
					QianHaiResult qianHaiResults = qianHaiResult_8079.getData();
					// E000990 查询中 轮询查询
					if (StringUtils.equals(qianHaiResults.getErCode(), "E000990")) {
						if (i == max) {
							throw new Exception("驾驶证对比处理中");
						}
						continue;
					}
					creditQueryResult.setChkDriverNo(qianHaiResults.getChkDriverNo());
					creditQueryResult.setChkName(qianHaiResults.getChkName());
					creditQueryResult.setChkStatus(qianHaiResults.getChkStatus());
					return;
				} else {
					throw new Exception(qianHaiResult_8079.getMsg());
				}
			}
		} else {
			throw new Exception(qianHaiResult_8081.getMsg());
		}
	}

	/**
	 * 对外投资
	 * 
	 * @param creditQueryResult
	 * @param hcRequestData
	 */
	@SuppressWarnings("unused")
	private void perinvestQuery(CreditQueryResult creditQueryResult, HundredCreditRequest hcRequestData) throws Exception {
		ResultInfo<String> perInvestResult = hundredCreditService.hundredCreditDasRequest(hcRequestData, MealType.PerInvest);
		if (perInvestResult.isSuccess()) {
			JSONObject creditJSON = JSON.parseObject(perInvestResult.getData());
			// 企业法人信息
			JSONArray ryposfrs = creditJSON.getJSONObject("product").getJSONArray("RYPOSFR");
			List<CreditPerInvest> ryposfrPerInvestList = new ArrayList<>();
			for (int i = 0; ryposfrs != null && i < ryposfrs.size(); i++) {
				CreditPerInvest creditPerInvest = new CreditPerInvest();
				creditPerInvest.setPerinvestType(CreditPerInvest.PERINVEST_TYPE_RYPOSFR);
				JSONObject ryposfrJSON = ryposfrs.getJSONObject(i);
				if (ryposfrJSON != null) {
					creditPerInvest.setEntstatus(ryposfrJSON.getString("ENTSTATUS"));
					creditPerInvest.setRegcap(ryposfrJSON.getString("REGCAP"));
					creditPerInvest.setEntname(ryposfrJSON.getString("ENTNAME"));
					creditPerInvest.setEnttype(ryposfrJSON.getString("ENTTYPE"));
					ryposfrPerInvestList.add(creditPerInvest);
				}

			}
			// 企业股东信息
			JSONArray ryposshas = creditJSON.getJSONObject("product").getJSONArray("RYPOSSHA");
			List<CreditPerInvest> ryposshaPerInvestList = new ArrayList<>();
			for (int i = 0; ryposshas != null && i < ryposshas.size(); i++) {
				CreditPerInvest creditPerInvest = new CreditPerInvest();
				creditPerInvest.setPerinvestType(CreditPerInvest.PERINVEST_TYPE_RYPOSSHA);
				JSONObject ryposshaJSON = ryposshas.getJSONObject(i);
				if (ryposshaJSON != null) {
					creditPerInvest.setEntstatus(ryposshaJSON.getString("ENTSTATUS"));
					creditPerInvest.setRegcap(ryposshaJSON.getString("REGCAP"));
					creditPerInvest.setEntname(ryposshaJSON.getString("ENTNAME"));
					creditPerInvest.setEnttype(ryposshaJSON.getString("ENTTYPE"));
					ryposshaPerInvestList.add(creditPerInvest);
				}
			}

			creditQueryResult.setCreditPerInvestList(ryposshaPerInvestList);
		} else {
			throw new CheckFailException(perInvestResult.getMsg());
		}
	}

	/**
	 * 根据三要素查询征信
	 * 
	 * @param name
	 * @param mobileNo
	 * @param idNo
	 * @return
	 */
	public CreditRequest findCreditByThreeElement(String name, String mobileNo, String idNo) {
		return creditRequestDAO.findCreditByThreeElement(name, mobileNo, idNo);
	}

}
