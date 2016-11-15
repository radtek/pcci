package com.pujjr.pcci.web;

import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.time.DateFormatUtils;
import org.pcci.api.bean.request.CreditRequestData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import com.alibaba.fastjson.JSON;
import com.pujjr.common.result.ResultInfo;
import com.pujjr.common.type.IdentityType;
import com.pujjr.common.type.credit.QueryReasonType;
import com.pujjr.common.utils.document.ExcelUtils;
import com.pujjr.pcci.dal.entity.CreditQueryResult;
import com.pujjr.pcci.service.credit.CreditService;
import com.pujjr.pcci.service.credit.ExcelService;

/**
 * @author wen
 * @date 创建时间：2016年10月11日 上午11:11:04
 *
 */
@Controller
public class IndexController extends BaseController {

	@Autowired
	CreditService creditService;

	@Autowired
	ExcelService excelService;

	@RequestMapping("/index")
	public String index() {
		return "index";
	}

	@RequestMapping("/credit")
	@ResponseBody
	public Object credit() {
		return creditService.creditQuery(new CreditRequestData());

	}

	@RequestMapping(value = "/creditQuery")
	public void downloadCreditQueryByExcel(@RequestParam MultipartFile[] files, HttpServletRequest request, HttpServletResponse response) {
		List<CreditQueryResult> creditQueryResultList = new ArrayList<>();
		if (files != null && files.length > 0) {
			// 循环获取files数组中得文件
			for (int i = 0; i < files.length; i++) {
				MultipartFile multipartFile = files[i];
				try {
					// 获取征信批量查询的Excel文件
					// 1.取Excel文件中叫Sheet的工作表或者第一个工作表
					// 2.表的第一排为标题,不需要读取
					// 3.文件中的字段顺序如下:
					// [姓名,手机号,证件号,授权码,授权时间]
					// 4.由于该批量处理为临时使用,以上字段外的征信查询需要的查询条件如证件类型,查询原因之类的固定写死,简化操作
					List<String[]> excelData = ExcelUtils.loadExcelFile(multipartFile.getInputStream(), multipartFile.getOriginalFilename(), 1, 5);
					CreditRequestData creditRequestData = new CreditRequestData();

					for (String[] excelStrArray : excelData) {
						// 从Excel里获取的必填
						creditRequestData.setName(excelStrArray[0]);// 姓名
						creditRequestData.setMobileNo(excelStrArray[1]);// 手机号
						creditRequestData.setIdNo(excelStrArray[2]);// 证件号
						creditRequestData.setEntityAuthCode(excelStrArray[3]);// 授权码
						creditRequestData.setEntityAuthDate(excelStrArray[4]);// 授权时间yyyy-MM-dd
						// 没从Excel里获取的 自动填写的值
						// creditRequestData.setCreditId(BaseUtils.newUUID());// 请求唯一标识流水
						creditRequestData.setIdType(IdentityType.ID_CARD.getCode());// 证件类型
						creditRequestData.setReasonCode(QueryReasonType.LOAN_APPROVAL.getCode());// 查询原因
						creditRequestData.setRequestUserId("admin");// 征信查询发起人
						System.out.println("------查询--" + excelStrArray[0] + "-----");
						creditQueryResultList.add(creditService.creditQuery(creditRequestData));
					}
					System.out.println("------查询--结束-----");
					System.out.println(JSON.toJSONString(excelData));
					ResultInfo<byte[]> excelResult = excelService.writeCreditExcelFile(creditQueryResultList);
					if (excelResult.isSuccess()) {
						byte[] byteArray = excelResult.getData();
						response.setContentType("application/x-download");
						response.setCharacterEncoding("utf-8");
						String fileName = "征信查询返回记录" + DateFormatUtils.format(new Date(), "yyyy-MM-dd") + "." + ExcelUtils.XLSX;
						/**
						 * 1. IE浏览器，采用URLEncoder编码 2. Opera浏览器，采用filename*方式 3. Safari浏览器，采用ISO编码的中文输出 4. Chrome浏览器，采用Base64编码或ISO编码的中文输出 5. FireFox浏览器，采用Base64或filename*或ISO编码的中文输出
						 * 
						 */
						// IE使用URLEncoder
						String userAgent = request.getHeader("User-Agent").toLowerCase();
						if (userAgent.contains("windows")) {
							fileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8.displayName());
							// 其它使用转iso
						} else {
							fileName = new String((fileName).getBytes(StandardCharsets.UTF_8.displayName()), "ISO8859-1");
						}
						response.addHeader("Content-Disposition", "attachment;filename=" + fileName);
						OutputStream os = response.getOutputStream();
						os.write(byteArray);
						os.flush();
						os.close();
					}

				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

	@RequestMapping(value = "/creditQueryByExcel")
	@ResponseBody
	public Object creditQueryByExcel(@RequestParam MultipartFile[] files) {
		List<CreditQueryResult> creditQueryResultList = new ArrayList<>();
		if (files != null && files.length > 0) {
			// 循环获取files数组中得文件
			for (int i = 0; i < files.length; i++) {
				MultipartFile multipartFile = files[i];
				try {
					// 获取征信批量查询的Excel文件
					// 1.取Excel文件中叫Sheet的工作表或者第一个工作表
					// 2.表的第一排为标题,不需要读取
					// 3.文件中的字段顺序如下:
					// [姓名,手机号,证件号,授权码,授权时间]
					// 4.由于该批量处理为临时使用,以上字段外的征信查询需要的查询条件如证件类型,查询原因之类的固定写死,简化操作
					List<String[]> excelData = ExcelUtils.loadExcelFile(multipartFile.getInputStream(), multipartFile.getOriginalFilename(), 1, 5);
					CreditRequestData creditRequestData = new CreditRequestData();

					for (String[] excelStrArray : excelData) {
						// 从Excel里获取的必填
						creditRequestData.setName(excelStrArray[0]);// 姓名
						creditRequestData.setMobileNo(excelStrArray[1]);// 手机号
						creditRequestData.setIdNo(excelStrArray[2]);// 证件号
						creditRequestData.setEntityAuthCode(excelStrArray[3]);// 授权码
						creditRequestData.setEntityAuthDate(excelStrArray[4]);// 授权时间yyyy-MM-dd
						// 没从Excel里获取的 自动填写的值
						// creditRequestData.setCreditId(BaseUtils.newUUID());// 请求唯一标识流水
						creditRequestData.setIdType(IdentityType.ID_CARD.getCode());// 证件类型
						creditRequestData.setReasonCode(QueryReasonType.LOAN_APPROVAL.getCode());// 查询原因
						creditRequestData.setRequestUserId("admin");// 征信查询发起人
						System.out.println("------查询--" + excelStrArray[0] + "-----");
						creditQueryResultList.add(creditService.creditQuery(creditRequestData));
					}
					System.out.println("------查询--结束-----");
					System.out.println(JSON.toJSONString(excelData));

				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		return creditQueryResultList;

	}

}
