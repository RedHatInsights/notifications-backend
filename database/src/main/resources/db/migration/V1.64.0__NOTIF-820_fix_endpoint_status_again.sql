UPDATE endpoints SET status = 'READY' WHERE not (endpoint_type =  3 AND endpoint_sub_type = 'slack');
