package net.javaonline.spring.jasper.controller;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.naming.NamingException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.jaspersoft.mongodb.MongoDbDataSource;
import com.jaspersoft.mongodb.connection.MongoDbConnection;

import net.javaonline.spring.jasper.form.JasperInputForm;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.JasperRunManager;
import net.sf.jasperreports.engine.export.HtmlExporter;
import net.sf.jasperreports.engine.util.JRLoader;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleHtmlExporterOutput;
import net.sf.jasperreports.export.SimpleHtmlReportConfiguration;

@Controller
public class LoadJasperReport {
	private static final Logger LOGGER = LoggerFactory.getLogger(LoadJasperReport.class); 

	@ModelAttribute("jasperRptFormats")
	public ArrayList<String> getJasperRptFormats(){
		ArrayList<String> jasperRptFormats = new ArrayList<String>();
		jasperRptFormats.add("Html");
		jasperRptFormats.add("PDF");

		return jasperRptFormats;
	}	


	@RequestMapping(value = "/loadJasper", method = RequestMethod.GET)
	public String loadSurveyPg(
			@ModelAttribute("jasperInputForm") JasperInputForm jasperInputForm,
			Model model) {
		model.addAttribute("JasperInputForm", jasperInputForm);
		return "loadJasper";
	}


	@RequestMapping(value = "/generateReport", method = RequestMethod.POST)
	public String generateReport(
			@Valid @ModelAttribute("jasperInputForm") JasperInputForm jasperInputForm,
			BindingResult result,Model model, 
			HttpServletRequest request, HttpServletResponse response) throws ParseException {
		LOGGER.debug("~~~ Generate Report ~~~");

		if (result.hasErrors()) {
			LOGGER.error("validation error occured in jasper input form");
			return "loadJasper";

		}

		String reportFileName = "JREmp1";

		Connection conn = null;
		try {
			MongoDbConnection mongConnection = new MongoDbConnection("mongodb://localhost:27017/mydb", null, null);

			Map<String, Object> parameters = new HashMap<String, Object>();
			parameters.put(MongoDbDataSource.QUERY_LANGUAGE, mongConnection);
			
			String rptFormat = jasperInputForm.getRptFmt();
			String noy = jasperInputForm.getNoofYears();

			LOGGER.debug("rpt format " + rptFormat+", no of years " + noy);


			HashMap<String,Object> hmParams=new HashMap<String,Object>();
			hmParams.put("noy", new Integer(noy));
			hmParams.put("Title", "Employees working more than "+ noy + " Years");


			JasperReport jasperReport = getCompiledFile(reportFileName, request);

			if (rptFormat.equalsIgnoreCase("html") ) {
				JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, hmParams, conn);
				// For HTML report
				generateReportHtml(jasperPrint, request, response); 
			}
			else if(rptFormat.equalsIgnoreCase("pdf")){
				// For PDF report
				generateReportPDF(response, hmParams, jasperReport, conn); 
			}
		} catch (Exception sqlExp) {
			LOGGER.error( "Exception::" + sqlExp.toString());
		} finally {
			try {
				if (conn != null) {
					conn.close();
					conn = null;
				}
			} catch (SQLException expSQL) {
				LOGGER.error("SQLExp::CLOSING::" + expSQL.toString());
			}
		}
		return null;
	}

	private JasperReport getCompiledFile(String fileName, HttpServletRequest request) throws JRException {
		LOGGER.debug("path " + request.getSession().getServletContext().getRealPath("/jasper/" + fileName + ".jasper"));
		
		File reportFile = new File( request.getSession().getServletContext().getRealPath("/jasper/" + fileName + ".jasper"));
		
		// If compiled file is not found, then compile XML template
		if (!reportFile.exists()) {
			JasperCompileManager.compileReportToFile(request.getSession().getServletContext().getRealPath("/jasper/" + fileName + ".jrxml"),request.getSession().getServletContext().getRealPath("/jasper/" + fileName + ".jasper"));
		}
		
		JasperReport jasperReport = (JasperReport) JRLoader.loadObjectFromFile(reportFile.getPath());
		return jasperReport;
	} 


	private void generateReportHtml( JasperPrint jasperPrint, HttpServletRequest req, HttpServletResponse resp) 
			throws IOException, JRException {
		LOGGER.debug("~~~ Generate HTML Report ~~~");
		
		HtmlExporter exporter=new HtmlExporter();
		
		List<JasperPrint> jasperPrintList = new ArrayList<JasperPrint>();
		jasperPrintList.add(jasperPrint);
		
		exporter.setExporterInput(SimpleExporterInput.getInstance(jasperPrintList));
		exporter.setExporterOutput( new SimpleHtmlExporterOutput(resp.getWriter()));
		
		SimpleHtmlReportConfiguration configuration =new SimpleHtmlReportConfiguration();
		exporter.setConfiguration(configuration);
		exporter.exportReport();
	}

	private void generateReportPDF (HttpServletResponse resp, Map<String, Object> parameters, 
			JasperReport jasperReport, Connection conn)throws JRException, NamingException, SQLException, IOException {
		LOGGER.debug("~~~ Generate PDF Report ~~~");
		
		byte[] bytes = null;
		bytes = JasperRunManager.runReportToPdf(jasperReport, parameters, conn);
		
		resp.reset();
		resp.resetBuffer();
		resp.setContentType("application/pdf");
		resp.setContentLength(bytes.length);
		ServletOutputStream ouputStream = resp.getOutputStream();
		ouputStream.write(bytes, 0, bytes.length);
		ouputStream.flush();
		ouputStream.close();
	} 
}