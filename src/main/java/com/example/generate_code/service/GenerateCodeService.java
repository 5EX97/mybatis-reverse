package com.example.generate_code.service;

import com.example.generate_code.model.ColumnClass;
import com.example.generate_code.model.RespBean;
import com.example.generate_code.model.TableClass;
import com.example.generate_code.utils.DBUtils;
import com.google.common.base.CaseFormat;
import freemarker.cache.ClassTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.springframework.stereotype.Service;

import java.io.*;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

@Service
public class GenerateCodeService {
    //配置freemarker
    Configuration cfg = null;

    {
        cfg = new Configuration(Configuration.VERSION_2_3_0);//版本
        cfg.setTemplateLoader(new ClassTemplateLoader(GenerateCodeService.class,"/templates"));//配置模板位置，第一个参数是类本身，第二个是模板位置
        cfg.setDefaultEncoding("UTF-8");
    }

    public RespBean generateCode(List<TableClass> tableClassList, String realPath) {
        try{
            Template modelTemplate = cfg.getTemplate("Model.java.ftl");
            Template mapperJavaTemplate = cfg.getTemplate("Mapper.java.ftl");
            Template mapperXmlTemplate = cfg.getTemplate("Mapper.xml.ftl");
            Template serviceTemplate = cfg.getTemplate("Service.java.ftl");
            Template controllerTemplate = cfg.getTemplate("Controller.java.ftl");
            Connection connection = DBUtils.getConnection();
            DatabaseMetaData metaData = connection.getMetaData();
            for(TableClass tableClass:tableClassList){
                //Connection接口的getCatalog()方法返回当前连接对象的当前目录/数据库的名称。
                ResultSet columns = metaData.getColumns(connection.getCatalog(), null, tableClass.getTableName(), null);
                ResultSet primaryKeys = metaData.getPrimaryKeys(connection.getCatalog(), null, tableClass.getTableName());
                List<ColumnClass> columnClassList = new ArrayList<>();
                while(columns.next()){
                    String column_name = columns.getString("COLUMN_NAME");
                    String type_name = columns.getString("TYPE_NAME");
                    String remarks = columns.getString("REMARKS");
                    ColumnClass columnClass= new ColumnClass();
                    columnClass.setRemark(remarks);
                    columnClass.setColumnName(column_name);
                    columnClass.setType(type_name);
                    columnClass.setPropertyName(CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL,column_name));
                    primaryKeys.first();//每次遍历得先让指针回到第一个
                    while(primaryKeys.next()){
                        String pkname = primaryKeys.getString("COLUMN_NAME");
                        if(column_name.equals(pkname)){
                            columnClass.setPrimary(true);
                        }
                    }
                    columnClassList.add(columnClass);
                }
                tableClass.setColumns(columnClassList);
                String path = realPath+"/"+tableClass.getPackageName().replace(".","/");
                generate(modelTemplate,tableClass,path+"/model/");
                generate(mapperJavaTemplate,tableClass,path+"/mapper/");
                generate(mapperXmlTemplate,tableClass,path+"/mapper/");
                generate(serviceTemplate,tableClass,path+"/service/");
                generate(controllerTemplate,tableClass,path+"/controller/");
            }
            return RespBean.ok("代码已生成",realPath);
        }catch (Exception e){
            e.printStackTrace();
        }
        return RespBean.error("代码生成失败");
    }

    private void generate(Template template, TableClass tableClass, String path) throws IOException, TemplateException {
        File folder = new File(path);
        if(!folder.exists()){
            folder.mkdirs();
        }
        String fileName = path+"/"+tableClass.getModelName()+template.getName().replace(".ftl","").replace("Model","");
        FileOutputStream fos = new FileOutputStream(fileName);
        OutputStreamWriter out = new OutputStreamWriter(fos);
        template.process(tableClass,out);//按模板 生成文件
        fos.close();
        out.close();
    }
}
